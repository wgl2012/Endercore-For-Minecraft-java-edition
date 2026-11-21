# -*- coding: utf-8 -*-
"""
局部寻路层（Local Pathfinding）。

在玩家周围小范围栅格上做 A* 绕障，输出可执行的步点序列与移动原语：
- walk     平地行走（move_to / move_rough）
- jump     翻越 1 格高差
- pillar   搭柱向上（pillar_up）
- bridge   搭桥向前（bridge_forward，过裂隙/水面）

数据约束：mod 只下发触达范围内的非空方块，空气方块不枚举。因此：
- 已知固体按碰撞体处理；
- 未登记的脚下方块默认按开阔地面放行，避免把平原误判成墙；
- 栅格信息不足或搜索超限时，退化为「直接朝向目标转向」，不强行绕障。
"""
import heapq
import math

RADIUS = 6
NODE_CAP = 250
ARRIVE_EXACT = 0.6
ARRIVE_FOLLOW = 1.2
FAR_DIST = 4.0

# 四方向水平偏移
_DIRS = [(1, 0), (-1, 0), (0, 1), (0, -1)]


class LocalPathfinder:
    def plan(self, player: dict, target: dict, blocks: list, mode: str = "walk") -> dict:
        if not target or "x" not in target:
            return self._no_path("无导航目标")

        px = float(player.get("x", 0.0))
        py = float(player.get("y", 0.0))
        pz = float(player.get("z", 0.0))
        tx = float(target["x"])
        tz = float(target["z"])
        ty = float(target.get("y", py))

        dist = math.hypot(tx - px, tz - pz)
        arrive_radius = ARRIVE_FOLLOW if mode == "follow" else ARRIVE_EXACT
        if dist <= arrive_radius:
            return {
                "found": True, "arrive": True, "waypoints": [], "next": None,
                "primitive": "stop_move", "reason": "已到达目标", "dist": round(dist, 2),
            }

        solids, openables, barriers = self._build_solids(blocks)
        start = (math.floor(px), math.floor(py), math.floor(pz))
        goal = (math.floor(tx), math.floor(ty), math.floor(tz))

        path = self._astar(start, goal, solids, openables, barriers)
        if path:
            waypoints = [(x + 0.5, z + 0.5) for (x, y, z) in path[1:]]
            nxt = path[1] if len(path) > 1 else start
            open_block = self._first_closed_door(path, openables)
            primitive, reason = self._primitive(
                player, nxt, ty, dist, solids, stepped_up=len(path) > 1 and path[1][1] > start[1]
            )
            if open_block is not None:
                primitive, reason = "open_door", "前方关闭的门/栅栏门，先开门通过"
            return {
                "found": True, "arrive": False, "waypoints": waypoints,
                "next": {"x": nxt[0] + 0.5, "y": nxt[1], "z": nxt[2] + 0.5},
                "open_block": open_block,
                "primitive": primitive, "reason": reason, "dist": round(dist, 2),
            }

        # 退化：直接朝目标转向
        primitive = "move_rough" if dist > FAR_DIST else "move_to"
        if ty - py > 1.2:
            primitive = "pillar_up"
        return {
            "found": False, "arrive": False, "waypoints": [],
            "next": {"x": tx, "y": ty, "z": tz},
            "primitive": primitive, "reason": "栅格信息不足，直接转向目标", "dist": round(dist, 2),
        }

    # ---------- 栅格 ----------

    @staticmethod
    def _build_solids(blocks: list):
        solids = set()
        openables = {}
        barriers = set()
        for b in blocks or []:
            try:
                key = (int(b["x"]), int(b["y"]), int(b["z"]))
            except (KeyError, TypeError, ValueError):
                continue
            if b.get("openable"):
                # 门/栅栏门/活板门：可开合通过，不计入硬阻挡
                openables[key] = b
            elif b.get("solid"):
                solids.add(key)
            if b.get("kind") in ("fence", "bars"):
                # 栅栏/铁栏杆：水平阻挡且不可翻越
                barriers.add(key)
        return solids, openables, barriers

    def _standable(self, cell: tuple, solids: set) -> bool:
        x, y, z = cell
        if (x, y, z) in solids or (x, y + 1, z) in solids:
            return False  # 身体或头部被挡
        # 脚下未登记默认视为开阔地面；已知空体方块不构成支撑但允许站立（水边/台阶由上层处理）
        return True

    def _neighbors(self, cell: tuple, solids: set, openables: dict, barriers: set):
        x, y, z = cell
        for dx, dz in _DIRS:
            horiz = (x + dx, y, z + dz)
            if self._standable(horiz, solids):
                cost = 1.4 if horiz in openables else 1.0
                yield horiz, cost
            # 跳上一格（栅栏/铁栏杆不可翻越）
            up = (x + dx, y + 1, z + dz)
            if (self._standable(up, solids) and (x + dx, y, z + dz) in solids
                    and horiz not in barriers):
                yield up, 1.6
        # 向下走一格
        down = (x, y - 1, z)
        if self._standable(down, solids):
            yield down, 1.0

    def _astar(self, start: tuple, goal: tuple, solids: set, openables: dict, barriers: set):
        if start == goal:
            return [start]

        def h(c):
            return math.dist(c, goal)

        open_heap = [(h(start), 0.0, start)]
        came = {}
        g_score = {start: 0.0}
        visited = set()
        expanded = 0

        while open_heap and expanded < NODE_CAP:
            _, g, cur = heapq.heappop(open_heap)
            if cur in visited:
                continue
            visited.add(cur)
            expanded += 1

            if math.dist(cur, goal) <= 1.5:
                return self._reconstruct(came, cur, start)

            for nxt, cost in self._neighbors(cur, solids, openables, barriers):
                if nxt in visited:
                    continue
                tentative = g + cost
                if tentative < g_score.get(nxt, float("inf")):
                    g_score[nxt] = tentative
                    came[nxt] = cur
                    heapq.heappush(open_heap, (tentative + h(nxt), tentative, nxt))

        return None

    @staticmethod
    def _first_closed_door(path: list, openables: dict):
        """返回路径上第一个关闭（open 不为 true）的可开合方块坐标；已开或未登记则跳过。"""
        for cell in path[1:]:
            b = openables.get(cell)
            if b is not None and b.get("open") is False:
                return {"x": cell[0], "y": cell[1], "z": cell[2]}
        return None

    @staticmethod
    def _reconstruct(came: dict, cur: tuple, start: tuple):
        path = [cur]
        while cur != start:
            cur = came[cur]
            path.append(cur)
        path.reverse()
        return path

    # ---------- 原语选择 ----------

    def _primitive(self, player, nxt, target_y, dist, solids, stepped_up: bool) -> tuple:
        py = float(player.get("y", 0.0))
        ny = nxt[1]
        in_water = bool(player.get("in_water", False))

        if target_y - py > 1.2:
            return "pillar_up", "需要上到更高处，搭柱爬升"
        if stepped_up or ny > py:
            return "jump", "翻越一格高差"
        # 前方脚下不是已知固体：疑似裂隙/水面，搭桥通过
        below = (nxt[0], ny - 1, nxt[2])
        if below not in solids and (in_water or dist > 2.0):
            return "bridge_forward", "前方疑似悬空/水面，搭桥通过"
        if dist > FAR_DIST:
            return "move_rough", "远距离疾跑接近"
        return "move_to", "精确步行接近"

    @staticmethod
    def _no_path(reason: str) -> dict:
        return {
            "found": False, "arrive": False, "waypoints": [], "next": None,
            "primitive": "idle", "reason": reason, "dist": None,
        }
