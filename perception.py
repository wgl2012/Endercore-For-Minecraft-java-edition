# -*- coding: utf-8 -*-
"""
感知层（Perception）。

把 mod 下发的原始状态归一化为结构化感知结果：
- 玩家状态 / 玩家短时位移趋势
- 敌对威胁（threats）与可交互实体（interactables），按距离排序
- 当前打开的界面 / 容器槽位
- 实体短时位置历史（为下一阶段「预测层」预留轨迹/意图推断）

本层只做采集与归一化，不做决策。
"""
import math
import time

# 视为"正在尝试移动"的动作集合；只有在移动意图下位移为 0 才算卡住，
# 避免主动 idle / 停下时被误判成 STUCK。
MOVE_ACTIONS = {
    "move_to", "move_rough", "go_to_block", "go_to_entity",
    "forward", "back", "left", "right", "bridge_forward",
    "pillar_up", "swim_up", "walk", "sprint",
}


class Perception:
    def __init__(self, history_limit: int = 8):
        self.history_limit = history_limit
        self.entity_history: dict = {}   # entity_id -> list[(t, x, z)]
        self.player_history: list = []     # list[(t, x, z)]

    @staticmethod
    def _trend(hist: list) -> dict:
        """根据最近两点位置估算移动方向/速度（格/秒）。"""
        if len(hist) < 2:
            return {"dx": 0.0, "dz": 0.0, "moving": False}
        t0, x0, z0 = hist[-2]
        t1, x1, z1 = hist[-1]
        dt = max(t1 - t0, 1e-3)
        dx = (x1 - x0) / dt
        dz = (z1 - z0) / dt
        return {
            "dx": round(dx, 3),
            "dz": round(dz, 3),
            "moving": math.hypot(dx, dz) > 0.05,
        }

    def update(self, state: dict, moving_intent: bool = False) -> dict:
        now = time.time()
        player = state.get("player") or {}

        # 玩家位置历史（卡住检测 / 控制闭环纠偏用）
        if "x" in player:
            self.player_history.append((now, player.get("x", 0.0), player.get("z", 0.0)))
            if len(self.player_history) > self.history_limit:
                self.player_history.pop(0)

        threats: list = []
        interactables: list = []

        for m in state.get("nearby_mobs") or []:
            eid = m.get("id")
            hist = self.entity_history.setdefault(eid, [])
            hist.append((now, m.get("x", 0.0), m.get("z", 0.0)))
            if len(hist) > self.history_limit:
                hist.pop(0)

            rec = dict(m)
            rec["trend"] = self._trend(hist)
            cat = m.get("category") or ("hostile" if m.get("hostile") else "passive")
            rec["category"] = cat
            dist = float(m.get("distance", 999))

            if cat == "hostile" and dist <= 16:
                threats.append(rec)
            if m.get("interactable") or cat == "trader":
                interactables.append(rec)

        threats.sort(key=lambda r: r.get("distance", 999))
        interactables.sort(key=lambda r: r.get("distance", 999))

        # 卡住提示：仅在有移动意图时，最近若干点横向位移几乎为 0 才算卡住
        stuck = False
        if moving_intent and len(self.player_history) >= 4:
            xs = [p[1] for p in self.player_history]
            zs = [p[2] for p in self.player_history]
            stuck = math.hypot(max(xs) - min(xs), max(zs) - min(zs)) < 0.08

        return {
            "player": player,
            "threats": threats,
            "interactables": interactables,
            "screen": state.get("screen") or {},
            "goal": state.get("goal"),
            "stuck": stuck,
            "player_trend": self._trend(self.player_history),
            "raw": state,
        }
