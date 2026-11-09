# -*- coding: utf-8 -*-
import json, sys
sys.path.insert(0, '.')
import llm_brain

state = {
    "player": {"x": 127.4, "y": 56.0, "z": 32.6, "health": 20.0, "hunger": 20.0, "air": 300, "in_water": False},
    "goal": "在原地搭一个6乘6乘6的立方体，材料为白色羊毛",
    "inventory": [{"name": "minecraft:white_wool", "count": 64}],
    "time": "day"
}
result = llm_brain.decide(state)
print("=== LLM 决策结果 ===")
print(json.dumps(result, ensure_ascii=False, indent=2))
