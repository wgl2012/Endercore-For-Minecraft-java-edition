# -*- coding: utf-8 -*-
"""
预测层（Prediction）。

在感知层归一化的基础上，做短时轨迹外推与意图推断：
- 实体位置历史 → 速度向量 → 未来 horizon 秒后的预测位置
- 相对玩家的接近速度 / 接触时间（ETA）
- 意图分类：chase（敌对追击）/ approach（靠近）/ flee（逃离）/ wander（游荡）

本层只做预测，不输出执行动作；结果供行为层、寻路层与 LLM 上下文使用。
"""
import math
import time


class Predictor:
    def __init__(self, history_limit: int = 8, horizon: float = 1.0, stale_after: float = 10.0):
        self.history_limit = history_limit
        self.horizon = horizon          # 外推时长（秒）
        self.stale_after = stale_after    # 超过该时间未见的实体清除历史
        self.history: dict = {}          # eid -> list[(t, x, y, z)]
        self._last_by_id: dict = {}      # 最近一次预测结果按 id 反查

    @staticmethod
    def _velocity(hist: list):
        if len(hist) < 2:
            return 0.0, 0.0, 0.0
        t0, x0, y0, z0 = hist[-2]
        t1, x1, y1, z1 = hist[-1]
        dt = max(t1 - t0, 1e-3)
        return (x1 - x0) / dt, (y1 - y0) / dt, (z1 - z0) / dt

    def _classify_intent(self, category: str, closing: float) -> str:
        if closing > 0.15:
            return "chase" if category == "hostile" else "approach"
        if closing < -0.15:
            return "flee"
        return "wander"

    def update(self, state: dict) -> dict:
        now = time.time()
        player = state.get("player") or {}
        px = float(player.get("x", 0.0))
        py = float(player.get("y", 0.0))
        pz = float(player.get("z", 0.0))

        threats: list = []
        interactables: list = []

        # 清理过期实体历史
        for eid in list(self.history.keys()):
            if self.history[eid] and now - self.history[eid][-1][0] > self.stale_after:
                self.history.pop(eid)

        for m in state.get("nearby_mobs") or []:
            eid = m.get("id")
            hist = self.history.setdefault(eid, [])
            hist.append((now, float(m.get("x", 0.0)), float(m.get("y", 0.0)), float(m.get("z", 0.0))))
            if len(hist) > self.history_limit:
                hist.pop(0)

            vx, vy, vz = self._velocity(hist)
            # 历史差分速度过小时，回退到 mod 下发的实体速度
            if abs(vx) < 1e-3:
                vx = float(m.get("vx", 0.0) or 0.0)
            if abs(vz) < 1e-3:
                vz = float(m.get("vz", 0.0) or 0.0)

            x = float(m.get("x", 0.0))
            y = float(m.get("y", 0.0))
            z = float(m.get("z", 0.0))
            pred_x = x + vx * self.horizon
            pred_z = z + vz * self.horizon

            dx = x - px
            dz = z - pz
            dist = math.hypot(dx, dz)
            # 接近速度：速度在指向玩家方向上的投影，正值=在靠近
            closing = 0.0
            if dist > 1e-3:
                closing = -(vx * dx + vz * dz) / dist

            category = m.get("category") or ("hostile" if m.get("hostile") else "passive")
            intent = self._classify_intent(category, closing)
            eta = round(dist / closing, 2) if closing > 0.15 else None

            rec = {
                "id": eid,
                "x": round(x, 2),
                "y": round(y, 2),
                "z": round(z, 2),
                "predicted_x": round(pred_x, 2),
                "predicted_y": round(y + vy * self.horizon, 2),
                "predicted_z": round(pred_z, 2),
                "velocity": {"vx": round(vx, 3), "vy": round(vy, 3), "vz": round(vz, 3)},
                "distance": round(dist, 2),
                "closing_speed": round(closing, 3),
                "intent": intent,
                "eta": eta,
                "category": category,
                "health": m.get("health"),
                "visible": m.get("visible", True),
                "blocker": m.get("blocker"),
                "profession": m.get("profession"),
                "tradable": m.get("tradable", True),
                "nbt": m.get("nbt"),
            }
            if category == "hostile" and dist <= 16:
                threats.append(rec)
            if m.get("interactable") or category == "trader":
                interactables.append(rec)

        threats.sort(key=lambda r: r["distance"])
        interactables.sort(key=lambda r: r["distance"])

        # 玩家自身短时趋势（供闭环判断是否在推进）
        pvx = float(player.get("vx", 0.0) or 0.0)
        pvz = float(player.get("vz", 0.0) or 0.0)
        player_pred = {
            "x": px + pvx * self.horizon,
            "z": pz + pvz * self.horizon,
            "velocity": {"vx": round(pvx, 3), "vz": round(pvz, 3)},
        }

        return {
            "threats": threats,
            "interactables": interactables,
            "player": player_pred,
            "horizon": self.horizon,
        }

    def find(self, entity_id) -> dict | None:
        """按实体 ID 从最近一次预测结果里取记录（由控制器缓存最新结果后调用）。"""
        return self._last_by_id.get(entity_id)

    def cache(self, prediction: dict):
        """缓存 id -> 记录，供 find 反查。"""
        self._last_by_id = {}
        for group in ("threats", "interactables"):
            for rec in prediction.get(group, []):
                self._last_by_id[rec["id"]] = rec
