# -*- coding: utf-8 -*-
"""
EnderCore AI 决策控制器 v3.1
只跑 LLM 大脑，去掉旧的写死算法模式
每次决策后把思考和动作推送到游戏内 HUD
v3.1：维护会话上下文，目标切换时清空历史，回喂上一步动作结果
"""
import time
import threading
import requests
from mod_bridge import bridge
import llm_brain
from perception import Perception, MOVE_ACTIONS
from behavior import BehaviorSelector
from prediction import Predictor
from pathfinding import LocalPathfinder
from watchdog import Watchdog


class AIController:
    def __init__(self):
        self.running = False
        self.thread = None
        self.loop_delay = 0.2
        self.log_callback = None
        self.last_llm_call = 0.0
        self.llm_interval = 2.5
        self.goal = None
        # 上一步动作及其执行结果，回喂给 LLM 作为下一步依据
        self.last_action_name = None
        self.last_action_ok = None
        # 类自动驾驶分层：感知层 + 行为状态机（本地，不经过 LLM）
        self.perception = Perception()
        self.behavior = BehaviorSelector()
        # 预测层：轨迹外推 / 意图；寻路层：局部绕障步点；闭环：卡死/超时/掉落兜底
        self.predictor = Predictor()
        self.pathfinder = LocalPathfinder()
        self.watchdog = Watchdog()

    def log(self, msg: str):
        if self.log_callback:
            self.log_callback(msg)

    def _push_hud(self, action="", status="思考中", balance=""):
        """推送状态到游戏内悬浮窗（WS 主通道，HTTP 兜底）；思考文本走聊天框。"""
        bridge.push_hud(action=action, status=status, balance=balance)

    @staticmethod
    def _format_balance(bal) -> str:
        if bal is None:
            return "—"
        try:
            return f"{int(float(bal)):,}"
        except (TypeError, ValueError):
            return str(bal)

    def start(self):
        if self.running:
            return
        self.running = True
        llm_brain.reset_history()
        self.last_action_name = None
        self.last_action_ok = None
        self.watchdog.reset()
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()
        self.log("🤖 LLM AI 已启动")
        self._push_hud(status="运行中")

    def stop(self):
        self.running = False
        bridge.stop_movement()
        self.log("⏹ AI 已停止")
        self._push_hud(status="已停止")

    def set_mode(self, mode: str):
        """兼容旧接口，现在只支持 LLM"""
        pass

    def _loop(self):
        while self.running:
            try:
                if not bridge.connected:
                    time.sleep(2)
                    continue
                now = time.time()
                if now - self.last_llm_call < self.llm_interval:
                    time.sleep(0.1)
                    continue
                self.last_llm_call = now

                state = bridge.snapshot()
                if not state or "player" not in state:
                    continue
                # 游戏内输入的目标（按 O 键设置）优先级最高
                in_game_goal = state.get("goal")
                if in_game_goal:
                    # 目标发生变化：清空会话历史，避免把旧任务上下文带进来
                    if self.goal != in_game_goal:
                        llm_brain.reset_history()
                        self.last_action_name = None
                        self.last_action_ok = None
                        self.watchdog.reset()
                        self.log("🎯 目标已切换，上下文已重置")
                    self.goal = in_game_goal
                if self.goal:
                    state["goal"] = self.goal

                # 决策间隔由 mod 端配置下发
                cfg = state.get("config") or {}
                if "llm_decision_interval_sec" in cfg:
                    try:
                        self.llm_interval = float(cfg["llm_decision_interval_sec"])
                    except Exception:
                        pass
                # 回喂上一步动作结果，让模型知道成败
                if self.last_action_name is not None:
                    state["last_action_result"] = {
                        "action": self.last_action_name,
                        "success": bool(self.last_action_ok),
                    }

                # 本地感知归一化 + 行为状态机决策（先于 LLM 任务层，结果作为上下文）
                moving_intent = self.last_action_name in MOVE_ACTIONS
                perc = self.perception.update(state, moving_intent=moving_intent)
                beh = self.behavior.update(perc)
                state["behavior"] = beh
                state["perception"] = {
                    "threats": [{"id": t.get("id"), "distance": t.get("distance"),
                                 "category": t.get("category"), "visible": t.get("visible", True),
                                 "blocker": t.get("blocker")} for t in perc["threats"]],
                    "interactables": [{"id": t.get("id"), "distance": t.get("distance"),
                                       "category": t.get("category"), "visible": t.get("visible", True),
                                       "blocker": t.get("blocker"), "profession": t.get("profession"),
                                       "tradable": t.get("tradable", True), "nbt": t.get("nbt")}
                                      for t in perc["interactables"]],
                    "stuck": perc["stuck"],
                }

                # 预测层：实体轨迹外推与意图，压缩后作为 LLM 上下文
                pred = self.predictor.update(state)
                self.predictor.cache(pred)
                state["prediction"] = self._compact_prediction(pred)

                self._push_hud(status=f"[{beh['state']}] 思考中...",
                               balance=self._format_balance(llm_brain.fetch_balance()))

                result = llm_brain.decide(state)

                if isinstance(result, dict) and result.get("fallback"):
                    self.log(f"⚠️ LLM 决策失败，兜底 idle: {result.get('reason')}")

                thought = ""
                action = {}
                if isinstance(result, dict) and "action" in result and isinstance(result["action"], dict):
                    thought = result.get("thought", "")
                    action = result["action"]
                else:
                    action = result

                action_name = action.get("action", "?")
                action_params = action.get("params", {}) or {}

                # 本地闭环：watchdog 安全兜底（卡死/超时/掉落）优先于 LLM 动作；
                # 寻路层只在导航类行为且 LLM 未给移动指令时补位绕障。
                target_pos = self._resolve_target(beh, action_name, action_params)
                wd = self.watchdog.evaluate(perc["player"], beh["state"], action_name, target_pos)
                refine_note = ""
                if wd["action"]:
                    action = wd["action"]
                    refine_note = wd["reason"]
                elif beh["state"] in ("NAVIGATE", "FOLLOW") and target_pos:
                    replan = wd["status"] == "timeout" or action_name in ("idle", "stop_move")
                    if replan:
                        if wd["status"] == "timeout":
                            bridge.stop_movement()
                        blocks = state.get("reachable_blocks") or []
                        plan = self.pathfinder.plan(
                            perc["player"], target_pos, blocks,
                            mode="follow" if beh["state"] == "FOLLOW" else "walk")
                        if plan.get("next"):
                            action = self._plan_action(plan)
                            refine_note = plan["reason"]

                # 可见性兜底：交互/攻击被遮挡目标时拦截，降级停止等待绕障，避免对墙空交互
                guard_action, guard_note = self._guard_visibility(action_name, action_params)
                if guard_action:
                    action = guard_action
                    refine_note = (refine_note + "·" + guard_note) if refine_note else guard_note

                action_name = action.get("action", "?")
                action_params = action.get("params", {}) or {}
                action_str = action_name + " " + str(action_params) if action_params else action_name

                if thought:
                    self.log(f"💭 {thought}")
                    bridge.push_chat(f"§b[EnderCore]§7 思考: {thought}")
                tag = f"{beh['state']}·{beh['reason']}"
                if refine_note:
                    tag += f"·{refine_note}"
                self.log(f"⚡ {action_str}  §8[{tag}]")
                balance_str = self._format_balance(llm_brain.fetch_balance())
                self._push_hud(action=action_str, status=f"[{beh['state']}/{wd['status']}] 执行中",
                                balance=balance_str)

                ok = self._execute_action(action)
                # 记录本轮动作结果，下一轮回喂
                self.last_action_name = action_name
                self.last_action_ok = ok
            except Exception as e:
                self.log(f"⚠️ AI 循环异常: {e}")
                self._push_hud(status=f"错误: {str(e)[:30]}")
                time.sleep(5)

    @staticmethod
    def _compact_prediction(pred: dict) -> dict:
        keys = ("id", "distance", "intent", "closing_speed", "eta", "predicted_x", "predicted_z",
                "visible", "blocker", "profession", "tradable", "nbt")

        def slim(rec: dict) -> dict:
            return {k: rec.get(k) for k in keys}

        return {
            "threats": [slim(r) for r in pred.get("threats", [])],
            "interactables": [slim(r) for r in pred.get("interactables", [])],
        }

    def _guard_visibility(self, action_name: str, params: dict) -> tuple[dict | None, str]:
        """执行前目标兜底：被墙/门/栅栏遮挡，或目标不可交易（未转职/幼年）时，否决交互/攻击/骑乘动作。"""
        if action_name not in ("interact_entity", "interact_entity_at", "attack_entity", "ride_entity"):
            return None, ""
        eid = params.get("id")
        if eid is None:
            return None, ""
        rec = self.predictor.find(eid)
        if rec is None:
            return None, ""
        if rec.get("visible", True) is False:
            blocker = rec.get("blocker") or "障碍"
            return {"action": "stop_move", "params": {}}, f"目标被{blocker}遮挡，已拦截交互"
        if rec.get("tradable", True) is False:
            return {"action": "stop_move", "params": {}}, "目标未转职/幼年，不可交易，已拦截交互"
        return None, ""

    def _resolve_target(self, beh: dict, action_name: str, params: dict) -> dict | None:
        """从行为目标（追踪实体预测位置）或 LLM 移动参数解析当前导航目标点。"""
        eid = beh.get("target")
        if beh["state"] in ("FOLLOW", "AVOID") and eid is not None:
            rec = self.predictor.find(eid)
            if rec:
                return {"x": rec["predicted_x"], "y": rec["predicted_y"], "z": rec["predicted_z"]}
        if action_name in ("move_to", "move_rough") and "x" in params:
            return {"x": params["x"], "z": params.get("z"), "y": params.get("y")}
        return None

    @staticmethod
    def _plan_action(plan: dict) -> dict:
        prim = plan.get("primitive", "move_to")
        nxt = plan.get("next") or {}
        if prim == "open_door" and plan.get("open_block"):
            ob = plan["open_block"]
            return {"action": "open_block", "params": {"x": ob["x"], "y": ob["y"], "z": ob["z"]}}
        if prim == "move_rough":
            return {"action": "move_rough", "params": {"x": nxt.get("x"), "z": nxt.get("z")}}
        if prim == "jump":
            return {"action": "jump", "params": {}}
        if prim == "pillar_up":
            return {"action": "pillar_up", "params": {"target_y": int(round(nxt.get("y", 0) + 1))}}
        if prim == "bridge_forward":
            return {"action": "bridge_forward", "params": {}}
        if prim == "stop_move":
            return {"action": "stop_move", "params": {}}
        return {"action": "move_to", "params": {"x": nxt.get("x"), "z": nxt.get("z")}}

    def _execute_action(self, action: dict) -> bool:
        a = action.get("action")
        p = action.get("params", {}) or {}
        if not a or a == "idle":
            return True
        # 所有元指令统一通过 send_action 下发
        ok = bridge.send_action(a, p)
        if not ok:
            self.log(f"⚠️ 动作执行失败: {a}")
        return ok


ai = AIController()
