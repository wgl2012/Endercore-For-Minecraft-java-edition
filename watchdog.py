# -*- coding: utf-8 -*-
"""
控制闭环 + Watchdog 安全兜底。

在「感知 → 预测 → 寻路」之后，对执行结果做闭环校验：
- stuck    移动意图下长时间近零位移 → 自愈动作阶梯（跳 → 侧移 → 侧移 → 搭柱）
- timeout  导航长时间没有缩短距离 → 停车并要求上层重规划
- falling  离地且快速下落 → 上浮 / 搭桥 / 跳跃自救
- arrived  进入到达半径 → 停车

只在导航类行为（NAVIGATE / FOLLOW / AVOID）下介入卡死与超时判断；
掉落自救属于安全兜底，任意状态下都生效。
"""
import math
import time

from perception import MOVE_ACTIONS

NAV_STATES = {"NAVIGATE", "FOLLOW", "AVOID"}

STUCK_MIN_DISPLACE = 0.12     # 两次评估间位移小于该值视为没挪动
STUCK_NEEDED = 3              # 连续多少次卡住才触发自愈
NAV_TIMEOUT = 12.0            # 导航总超时（秒）
PROGRESS_GRACE = 6.0         # 多久没缩短距离判为超时
FALL_VY = -0.6               # 垂直下落速度阈值（格/tick 量级）
ARRIVE_RADIUS = 0.8

# 自愈动作阶梯：依次尝试不同脱困方向
_HEAL_CYCLE = [
    {"action": "jump", "params": {}},
    {"action": "left", "params": {"on": True}},
    {"action": "right", "params": {"on": True}},
    {"action": "pillar_up", "params": {}},
]


class Watchdog:
    def __init__(self):
        self.reset()

    def reset(self):
        self.task_start = time.time()
        self.last_pos = None
        self.best_dist = None
        self.last_progress = time.time()
        self.stuck_count = 0
        self.heal_step = 0

    def evaluate(self, player: dict, behavior_state: str, action_name: str,
                 target_pos: dict | None, now: float | None = None) -> dict:
        now = now or time.time()
        px = float(player.get("x", 0.0))
        py = float(player.get("y", 0.0))
        pz = float(player.get("z", 0.0))
        on_ground = bool(player.get("on_ground", True))
        vy = float(player.get("vy", 0.0) or 0.0)
        in_water = bool(player.get("in_water", False))

        # 1) 掉落自救（安全兜底，任意状态生效）
        if not on_ground and vy < FALL_VY:
            if in_water:
                return {"status": "falling", "reason": "水中下沉，上浮自救",
                        "action": {"action": "swim_up", "params": {}}}
            if target_pos and math.hypot(target_pos["x"] - px, target_pos["z"] - pz) > 1.0:
                return {"status": "falling", "reason": "坠落中，向前搭桥自救",
                        "action": {"action": "bridge_forward", "params": {}}}
            return {"status": "falling", "reason": "坠落中，尝试跳跃缓冲",
                    "action": {"action": "jump", "params": {}}}

        # 非导航行为不做卡死/超时/到达判断
        if behavior_state not in NAV_STATES:
            return {"status": "ok", "reason": "", "action": None}

        # 2) 到达判断（AVOID 的目标是威胁本体，不能在敌人身边停车）
        dist = None
        if target_pos:
            dist = math.hypot(target_pos["x"] - px, target_pos["z"] - pz)
            if behavior_state in ("NAVIGATE", "FOLLOW") and dist <= ARRIVE_RADIUS:
                return {"status": "arrived", "reason": "已进入目标半径",
                        "action": {"action": "stop_move", "params": {}}}
            # 推进记录
            if self.best_dist is None or dist < self.best_dist - 0.3:
                self.best_dist = dist
                self.last_progress = now

        # 3) 卡死检测（仅在本回合意图为移动时）
        if action_name in MOVE_ACTIONS:
            if self.last_pos is not None:
                moved = math.hypot(px - self.last_pos[0], pz - self.last_pos[1])
                if moved < STUCK_MIN_DISPLACE:
                    self.stuck_count += 1
                else:
                    self.stuck_count = 0
            self.last_pos = (px, pz)

            if self.stuck_count >= STUCK_NEEDED:
                action = _HEAL_CYCLE[self.heal_step % len(_HEAL_CYCLE)]
                self.heal_step += 1
                self.stuck_count = 0
                return {"status": "stuck", "reason": "移动受阻，执行自愈脱困", "action": action}

        # 4) 导航超时：总时长超限且近期无推进。
        # 不直接压 stop_move（会打断重规划形成死循环）；只置 timeout 状态，
        # 由控制器据此触发寻路补位重新规划步点。
        if target_pos and (now - self.task_start > NAV_TIMEOUT) and \
                (now - self.last_progress > PROGRESS_GRACE):
            return {"status": "timeout", "reason": "导航超时无推进，触发重规划",
                    "action": None}

        return {"status": "ok", "reason": "", "action": None}
