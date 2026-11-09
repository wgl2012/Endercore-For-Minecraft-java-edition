# -*- coding: utf-8 -*-
import json, requests
with open('config.json', encoding='utf-8') as f:
    cfg = json.load(f)
for model in [
    "qwen/qwen-2.5-72b-instruct:free",
    "qwen/qwen-2.5-7b-instruct:free",
    "meta-llama/llama-3.3-70b-instruct:free",
    "deepseek/deepseek-chat:free",
]:
    payload = {"model": model, "messages": [{"role": "user", "content": "hi"}], "max_tokens": 5}
    try:
        r = requests.post(
            cfg["llm_base_url"] + "/chat/completions",
            headers={"Authorization": "Bearer " + cfg["llm_api_key"]},
            json=payload, timeout=15,
        )
        print(f"{model}: {r.status_code} {r.text[:200]}")
    except Exception as e:
        print(f"{model}: ERROR {e}")
