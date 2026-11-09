# -*- coding: utf-8 -*-
import json, requests, sys
sys.path.insert(0, '.')
with open('config.json', encoding='utf-8') as f:
    cfg = json.load(f)
import llm_brain

state = {
    "player": {"x": 127.4, "y": 56.0, "z": 32.6, "health": 20.0, "hunger": 20.0, "air": 300, "in_water": False},
    "goal": "在原地搭一个6乘6乘6的立方体，材料为白色羊毛",
    "inventory": [{"name": "minecraft:white_wool", "count": 64}],
    "time": "day"
}

payload = {
    "model": cfg["llm_model"],
    "messages": [
        {"role": "system", "content": llm_brain.SYSTEM_PROMPT},
        {"role": "user", "content": json.dumps(state, ensure_ascii=False)},
    ],
    "temperature": 0.3,
    "max_tokens": 400,
}
r = requests.post(
    cfg["llm_base_url"] + "/chat/completions",
    headers={"Authorization": "Bearer " + cfg["llm_api_key"]},
    json=payload, timeout=20,
)
print("=== 完整返回 ===")
print(r.status_code)
print(r.text[:2000])
