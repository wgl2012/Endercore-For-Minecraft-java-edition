# -*- coding: utf-8 -*-
"""在 (143,56,8) 建 4x5x4 长方体"""
import requests, time

BASE = "http://127.0.0.1:25599"

# 停止移动
requests.post(BASE + "/api/action", json={"action": "stop_move"}, timeout=5)

def place(x, y, z):
    try:
        r = requests.post(BASE + "/api/action",
            json={"action": "place_block_at", "params": {"x": x, "y": y, "z": z}},
            timeout=5)
        return r.json().get("success", False)
    except Exception as e:
        return False

# 长方体：x 143..146, y 56..60, z 8..11
placed = 0
failed = 0
for y in range(56, 61):
    for z in range(8, 12):
        for x in range(143, 147):
            ok = place(x, y, z)
            if ok:
                placed += 1
                print(f"[{placed}] ({x},{y},{z})")
            else:
                failed += 1
                print(f"  FAIL ({x},{y},{z})")
            time.sleep(0.12)

print(f"\n完成！成功 {placed}，失败 {failed}")
