# -*- coding: utf-8 -*-
"""
EnderCore AI 决策控制器 v3.0
只跑 LLM 大脑，去掉旧的写死算法模式
每次决策后把思考和动作推送到游戏内 HUD
"""
import time
import threading
import requests
from mod_bridge import bridge


class AIController:
    def __init__(self):
        self.running = False
        self.thread = None
        self.loop_delay = 0.2
        self.log_callback = None
        self.last_llm_call = 0.0
        self.llm_interval = 2.5
        self.goal = None

    def log(self, msg: str):
        if self.log_callback:
            self.log_callback(msg)

    def _push_hud(self, thought="", action="", status="思考中"):
        """推送状态到游戏内悬浮窗"""
        try:
            requests.post("http://127.0.0.1:25599/api/hud", json={
                "thought": thought,
                "action": action,
                "status": status
            }, timeout=1)
        except Exception:
            pass

    def start(self):
        if self.running:
            return
        self.running = True
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
                    time.sleep(0.5)
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
                    self.goal = in_game_goal
                try:
                    state["blocks"] = bridge.get_blocks()
                except Exception:
                    pass
                if self.goal:
                    state["goal"] = self.goal

                self._push_hud(status="思考中...", thought="正在分析游戏状态...")

                import llm_brain
                result = llm_brain.decide(state)

                thought = ""
                action = {}
                if isinstance(result, dict) and "action" in result and isinstance(result["action"], dict):
                    thought = result.get("thought", "")
                    action = result["action"]
                else:
                    action = result

                action_name = action.get("action", "?")
                action_params = action.get("params", {})
                action_str = action_name + " " + str(action_params) if action_params else action_name

                if thought:
                    self.log(f"💭 {thought}")
                self.log(f"⚡ {action_str}")
                self._push_hud(thought=thought, action=action_str, status="执行中")

                self._execute_action(action)
            except Exception as e:
                self.log(f"⚠️ AI 循环异常: {e}")
                self._push_hud(status=f"错误: {str(e)[:30]}")
                time.sleep(2)

    def _execute_action(self, action: dict):
        a = action.get("action")
        p = action.get("params", {}) or {}
        if not a or a == "idle":
            return
        # 所有元指令统一通过 send_action 下发
        ok = bridge.send_action(a, p)
        if not ok:
            self.log(f"⚠️ 动作执行失败: {a}")


ai = AIController()
