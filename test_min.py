# -*- coding: utf-8 -*-
import json, requests
with open('config.json', encoding='utf-8') as f:
    cfg = json.load(f)
payload = {
    "model": cfg["llm_model"],
    "messages": [{"role": "user", "content": "你好，回复一个字"}],
    "max_tokens": 10,
}
r = requests.post(
    cfg["llm_base_url"] + "/chat/completions",
    headers={"Authorization": "Bearer " + cfg["llm_api_key"]},
    json=payload, timeout=15,
)
print(r.status_code)
print(r.text[:1000])
