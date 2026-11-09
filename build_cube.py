# -*- coding: utf-8 -*-
"""在玩家旁边搭一个 6x6x6 立方体"""
import requests, time

BASE = "http://127.0.0.1:25599"

def place(x, y, z):
    try:
        r = requests.post(BASE + "/api/action",
            json={"action": "place_block_at", "params": {"x": x, "y": y, "z": z}},
            timeout=5)
        return r.json().get("success", False)
    except Exception as e:
        print(f"  error at ({x},{y},{z}): {e}")
        return False

# 玩家在 (127, 56, 32)，立方体范围：
# x: 125..130, y: 56..61, z: 30..35
placed = 0
failed = 0
for y in range(56, 62):
    for z in range(30, 36):
        for x in range(125, 131):
            # 跳过玩家站的位置
            if x == 127 and y == 56 and z == 32:
                continue
            ok = place(x, y, z)
            if ok:
                placed += 1
                print(f"[{placed}] placed ({x},{y},{z})")
            else:
                failed += 1
                print(f"  fail ({x},{y},{z})")
            time.sleep(0.15)  # 稍微放慢，避免卡

print(f"\n完成！成功 {placed} 块，失败 {failed} 块")
