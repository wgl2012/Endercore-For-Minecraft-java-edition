# -*- coding: utf-8 -*-
"""
EnderCore 模组通信桥
负责和 Minecraft Fabric 模组之间收发数据

要点：
- 动作执行必须解析 body 里的 success 字段，不能只看 HTTP 状态码
- 状态数据走轮询缓存，AI 循环和 GUI 刷新不再每帧发请求
"""
import requests
import threading
import time
from typing import Optional, Dict, List

# 模组内嵌的 HTTP 服务器地址（模组端默认跑在这个端口）
DEFAULT_HOST = "http://127.0.0.1:25599"


class ModBridge:
    def __init__(self, host: str = DEFAULT_HOST):
        self.host = host
        self.connected = False
        self.game_data: Dict = {}
        self._lock = threading.Lock()
        self._polling = False
        self._poll_thread: Optional[threading.Thread] = None

    # ---------- 连接 ----------
    def test_connection(self) -> bool:
        """测试和模组的连接"""
        try:
            r = requests.get(f"{self.host}/api/ping", timeout=2)
            self.connected = r.ok
            return self.connected
        except Exception:
            self.connected = False
            return False

    # ---------- 数据轮询（后台线程） ----------
    def start_polling(self, interval: float = 0.25):
        """后台线程定时拉取游戏数据，写入缓存"""
        if self._polling:
            return
        self._polling = True
        self._poll_thread = threading.Thread(target=self._poll_loop, args=(interval,), daemon=True)
        self._poll_thread.start()

    def stop_polling(self):
        self._polling = False

    def _poll_loop(self, interval: float):
        while self._polling:
            try:
                r = requests.get(f"{self.host}/api/state", timeout=2)
                if r.ok:
                    data = r.json()
                    with self._lock:
                        self.game_data = data
                    # 读不到世界时 connected 仍可能被重置：保持连接状态
                    if data.get("error"):
                        self.connected = True
            except Exception:
                # 连续失败判定为掉线
                self.connected = False
            time.sleep(interval)

    def snapshot(self) -> Dict:
        """读取一份游戏数据快照（线程安全）"""
        with self._lock:
            return self.game_data

    # ---------- 查询（读缓存，不发请求） ----------
    def get_game_state(self) -> Dict:
        return self.snapshot()

    def get_blocks(self) -> Dict:
        """请求一次周围方块扫描（不读缓存）"""
        try:
            r = requests.get(f"{self.host}/api/blocks", timeout=2)
            return r.json() if r.ok else {}
        except Exception:
            return {}

    def break_block(self, x: float, y: float, z: float):
        return self.send_action("break_block", {"x": x, "y": y, "z": z})

    def select_slot(self, slot: int):
        return self.send_action("select_slot", {"slot": slot})

    def drop_item(self):
        return self.send_action("drop_item")

    def use_item(self):
        return self.send_action("use_item")

    def interact_entity(self, eid: int):
        return self.send_action("interact_entity", {"id": eid})

    def craft(self, recipe: str):
        return self.send_action("craft", {"recipe": recipe})

    def bridge_forward(self):
        """前下方 45° 放一块延伸地板（过峡谷/沟壑）"""
        return self.send_action("bridge_forward")

    def pillar_up(self, target_y=None):
        """跳起来在脚下放一块，原地搭柱子爬高。target_y 为目标高度，到了自动停。"""
        params = {}
        if target_y is not None:
            params["target_y"] = target_y
        return self.send_action("pillar_up", params)

    def place_block_at(self, x: float, y: float, z: float):
        """在指定坐标精确放方块"""
        return self.send_action("place_block_at", {"x": x, "y": y, "z": z})

    def get_player_info(self) -> Dict:
        return self.snapshot().get("player", {})

    def get_nearby_mobs(self, radius: float = 32.0) -> List[Dict]:
        """按半径过滤附近生物"""
        mobs = self.snapshot().get("nearby_mobs", [])
        return [m for m in mobs if m.get("distance", 999) <= radius]

    # ---------- 动作 ----------
    def send_action(self, action: str, params: Dict = None) -> bool:
        """下发动作，返回模组端真正执行成功与否"""
        payload = {"action": action, "params": params or {}}
        try:
            r = requests.post(f"{self.host}/api/action", json=payload, timeout=3)
            body = r.json()
            return bool(body.get("success", False))
        except Exception:
            return False

    # ---------- 快捷动作 ----------
    def move_toward(self, x: float, y: float, z: float):
        """移动到指定坐标（持续控制层会一直走到）"""
        return self.send_action("move_to", {"x": x, "z": z})

    def move_rough(self, x: float, y: float, z: float):
        return self.send_action("move_rough", {"x": x, "z": z})

    def attack_nearest(self, mob_type: str = None):
        """攻击最近的敌对怪物（可指定类型，预留）"""
        return self.send_action("attack_nearest", {"type": mob_type})

    def look_at(self, x: float, y: float, z: float):
        """看向指定坐标"""
        return self.send_action("look_at", {"x": x, "y": y, "z": z})

    def stop_movement(self):
        """停止移动"""
        return self.send_action("stop_move")

    def swim_up(self):
        """水里上浮自救"""
        return self.send_action("swim_up")

    def place_block(self, slot: int = 0):
        """在准星指向处放置/使用手中物品"""
        return self.send_action("place_block", {"slot": slot})

    def swing_hand(self):
        """挥手（攻击动作）"""
        return self.send_action("swing")


# 全局单例
bridge = ModBridge()
