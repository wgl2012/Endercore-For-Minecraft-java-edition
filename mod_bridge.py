# -*- coding: utf-8 -*-
"""
EnderCore 模组通信桥 v3.1

传输策略：
- 主通道：WebSocket 长连接（默认 ws://127.0.0.1:25600）
  - mod 主动推送游戏状态，Python 不再轮询
  - 动作 / HUD 帧下行，动作结果通过 action_result 帧回传
- 兜底通道：HTTP REST（http://127.0.0.1:25599）
  - WS 未连接时，动作/HUD 自动回退到 HTTP
  - 健康检查仍走 HTTP /api/ping
"""
import json
import threading
import time
from typing import Optional, Dict, List

import requests

try:
    import websocket  # websocket-client
    _WS_AVAILABLE = True
except ImportError:
    websocket = None
    _WS_AVAILABLE = False

DEFAULT_HOST = "http://127.0.0.1:25599"
DEFAULT_WS_URL = "ws://127.0.0.1:25600"


class ModBridge:
    def __init__(self, host: str = DEFAULT_HOST, ws_url: str = DEFAULT_WS_URL):
        self.host = host
        self.ws_url = ws_url
        self.connected = False
        self.game_data: Dict = {}
        self._lock = threading.Lock()

        # 兜底 HTTP 轮询
        self._polling = False
        self._poll_thread: Optional[threading.Thread] = None

        # WebSocket
        self.ws: Optional[websocket.WebSocketApp] = None
        self.ws_connected = False
        self._ws_running = False
        self._ws_thread: Optional[threading.Thread] = None

        # 动作结果等待（动作串行下发，单 pending 即可）
        self._result_event = threading.Event()
        self._result_value: Optional[bool] = None

    # ---------- 连接 ----------
    def test_connection(self) -> bool:
        """HTTP 健康检查，并确保 WS 重连循环已启动。"""
        try:
            r = requests.get(f"{self.host}/api/ping", timeout=2)
            self.connected = r.ok
        except Exception:
            self.connected = False

        if _WS_AVAILABLE:
            self._ensure_ws_thread()
        return self.connected

    # ---------- WebSocket ----------
    def _ensure_ws_thread(self):
        if not self._ws_running:
            self._ws_running = True
            self._ws_thread = threading.Thread(target=self._ws_loop, daemon=True)
            self._ws_thread.start()

    def _ws_loop(self):
        """WS 断线自动重连，指数退避兜底为 2~5 秒。"""
        backoff = 2
        while self._ws_running:
            try:
                self.ws = websocket.WebSocketApp(
                    self.ws_url,
                    on_open=self._on_ws_open,
                    on_message=self._on_ws_message,
                    on_error=self._on_ws_error,
                    on_close=self._on_ws_close,
                )
                self.ws.run_forever(ping_interval=10, ping_timeout=5)
            except Exception as e:
                pass
            self.ws_connected = False
            if not self._ws_running:
                break
            time.sleep(backoff)
            backoff = min(backoff + 1, 5)

    def _on_ws_open(self, ws):
        self.ws_connected = True
        self.connected = True

    def _on_ws_message(self, ws, message):
        try:
            msg = json.loads(message)
        except Exception:
            return
        mtype = msg.get("type")
        if mtype == "state":
            with self._lock:
                self.game_data = msg.get("data") or {}
        elif mtype == "action_result":
            self._result_value = bool(msg.get("success", False))
            self._result_event.set()
        # pong 无需处理

    def _on_ws_error(self, ws, error):
        self.ws_connected = False

    def _on_ws_close(self, ws, code, msg):
        self.ws_connected = False

    # ---------- 兜底 HTTP 轮询（仅 WS 未连接时） ----------
    def start_polling(self, interval: float = 0.25):
        if self._polling:
            return
        self._polling = True
        self._poll_thread = threading.Thread(target=self._poll_loop, args=(interval,), daemon=True)
        self._poll_thread.start()
        if _WS_AVAILABLE:
            self._ensure_ws_thread()

    def stop_polling(self):
        self._polling = False
        self._ws_running = False

    def _poll_loop(self, interval: float):
        while self._polling:
            # WS 已连接时状态由推送更新，跳过 HTTP 请求
            if not self.ws_connected:
                try:
                    r = requests.get(f"{self.host}/api/state", timeout=2)
                    if r.ok:
                        data = r.json()
                        with self._lock:
                            self.game_data = data
                        if data.get("error"):
                            self.connected = True
                except Exception:
                    self.connected = False
            time.sleep(interval)

    def snapshot(self) -> Dict:
        with self._lock:
            return self.game_data

    # ---------- 查询（读缓存，不发请求） ----------
    def get_game_state(self) -> Dict:
        return self.snapshot()

    def get_blocks(self) -> Dict:
        try:
            r = requests.get(f"{self.host}/api/blocks", timeout=2)
            return r.json() if r.ok else {}
        except Exception:
            return {}

    def get_player_info(self) -> Dict:
        return self.snapshot().get("player", {})

    def get_nearby_mobs(self, radius: float = 32.0) -> List[Dict]:
        mobs = self.snapshot().get("nearby_mobs", [])
        return [m for m in mobs if m.get("distance", 999) <= radius]

    # ---------- 动作 ----------
    def send_action(self, action: str, params: Dict = None) -> bool:
        """优先走 WebSocket 并等待 action_result；失败回退 HTTP。"""
        params = params or {}

        if self.ws_connected and self.ws is not None:
            try:
                self._result_event.clear()
                self._result_value = None
                frame = {"type": "action", "action": action, "params": params}
                self.ws.send(json.dumps(frame, ensure_ascii=False))
                if self._result_event.wait(timeout=3.0):
                    return bool(self._result_value)
                # 超时未收到结果，回退 HTTP
            except Exception:
                pass

        # HTTP 兜底
        payload = {"action": action, "params": params}
        try:
            r = requests.post(f"{self.host}/api/action", json=payload, timeout=3)
            body = r.json()
            return bool(body.get("success", False))
        except Exception:
            return False

    # ---------- HUD 推送 ----------
    def push_hud(self, thought: str = "", action: str = "", status: str = "", balance: str = ""):
        frame = {"type": "hud", "thought": thought, "action": action, "status": status, "balance": balance}
        if self.ws_connected and self.ws is not None:
            try:
                self.ws.send(json.dumps(frame, ensure_ascii=False))
                return
            except Exception:
                pass
        # HTTP 兜底
        try:
            requests.post(f"{self.host}/api/hud", json=frame, timeout=1)
        except Exception:
            pass

    # ---------- 聊天框推送 ----------
    def push_chat(self, message: str):
        frame = {"type": "chat", "message": message}
        if self.ws_connected and self.ws is not None:
            try:
                self.ws.send(json.dumps(frame, ensure_ascii=False))
                return
            except Exception:
                pass
        # HTTP 兜底
        try:
            requests.post(f"{self.host}/api/chat", json=frame, timeout=1)
        except Exception:
            pass

    # ---------- 快捷动作 ----------
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
        return self.send_action("bridge_forward")

    def pillar_up(self, target_y=None):
        params = {}
        if target_y is not None:
            params["target_y"] = target_y
        return self.send_action("pillar_up", params)

    def place_block_at(self, x: float, y: float, z: float):
        return self.send_action("place_block_at", {"x": x, "y": y, "z": z})

    def move_toward(self, x: float, y: float, z: float):
        return self.send_action("move_to", {"x": x, "z": z})

    def move_rough(self, x: float, y: float, z: float):
        return self.send_action("move_rough", {"x": x, "z": z})

    def attack_nearest(self, mob_type: str = None):
        return self.send_action("attack_nearest", {"type": mob_type})

    def look_at(self, x: float, y: float, z: float):
        return self.send_action("look_at", {"x": x, "y": y, "z": z})

    def stop_movement(self):
        return self.send_action("stop_move")

    def swim_up(self):
        return self.send_action("swim_up")

    def place_block(self, slot: int = 0):
        return self.send_action("place_block", {"slot": slot})

    def swing_hand(self):
        return self.send_action("swing")


# 全局单例
bridge = ModBridge()
