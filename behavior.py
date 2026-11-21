# -*- coding: utf-8 -*-
"""
行为规划层（Behavior，本地状态机骨架）。

分层位置（类自动驾驶架构）：
    任务层(LLM) → 行为层(本模块) → 局部寻路(待建) → 控制闭环(待建) → 执行

状态机优先级（高 → 低）：
    FIGHT / AVOID  安全兜底：敌对生物近身战斗，逼近则避让
    STUCK          卡住自愈：移动受阻，等待下层自愈/重规划
    INTERACT       界面已打开，执行交易/合成等容器操作
    FOLLOW         可交互实体在远处，先接近
    NAVIGATE       有任务目标，前往目标
    IDLE           无目标、无威胁

当前阶段只输出「行为意图」(state / reason / target)，
具体轨迹、步点、避障由后续局部寻路与控制层落地。
"""
from enum import Enum


class Behavior(str, Enum):
    IDLE = "IDLE"
    NAVIGATE = "NAVIGATE"
    FOLLOW = "FOLLOW"
    INTERACT = "INTERACT"
    BUILD = "BUILD"
    FIGHT = "FIGHT"
    AVOID = "AVOID"
    STUCK = "STUCK"


class BehaviorSelector:
    def __init__(self):
        self.state = Behavior.IDLE
        self.reason = ""
        self.target = None

    def update(self, perc: dict) -> dict:
        threats = perc.get("threats", [])
        interact = perc.get("interactables", [])
        screen = perc.get("screen") or {}
        goal = perc.get("goal")

        # 1) 安全：近身敌对 → 战斗；中距离逼近 → 避让
        if threats:
            t = threats[0]
            if t.get("visible", True) and float(t.get("distance", 999)) <= 4.0:
                return self._set(Behavior.FIGHT, "敌对生物近身", t.get("id"))
            return self._set(Behavior.AVOID, "敌对生物接近", t.get("id"))

        # 2) 卡住自愈
        if perc.get("stuck"):
            return self._set(Behavior.STUCK, "移动受阻，待自愈", None)

        # 3) 已打开容器 / 交易界面 → 交互
        if screen.get("open"):
            return self._set(Behavior.INTERACT, "界面已打开", None)

        # 4) 可交互实体在附近 → 先跟随接近，到身旁且视线通畅再交互
        if interact:
            # 优先挑选可交易目标（已转职成年村民）；明确不可交易(tradable=False)不发起交易
            tradable_pool = [t for t in interact if t.get("tradable", True)]
            pool = tradable_pool if tradable_pool else interact
            t = pool[0]
            visible = t.get("visible", True)
            blocker = t.get("blocker")
            if not visible:
                return self._set(Behavior.FOLLOW, f"被{blocker or '障碍'}遮挡，需绕障/开门", t.get("id"))
            if t.get("tradable", True) is False:
                return self._set(Behavior.NAVIGATE, "无可交易村民（未转职/幼年）", None)
            if float(t.get("distance", 999)) > 2.5:
                return self._set(Behavior.FOLLOW, "接近可交互实体", t.get("id"))
            return self._set(Behavior.INTERACT, "可交互实体已在身旁", t.get("id"))

        # 5) 有任务目标 → 导航（建造/采集等细分类型后续由任务层补充）
        if goal:
            return self._set(Behavior.NAVIGATE, "前往目标", None)

        return self._set(Behavior.IDLE, "无目标", None)

    def _set(self, state: Behavior, reason: str, target) -> dict:
        self.state = state
        self.reason = reason
        self.target = target
        return {"state": state.value, "reason": reason, "target": target}
