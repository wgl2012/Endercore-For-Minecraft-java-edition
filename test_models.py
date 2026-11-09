# -*- coding: utf-8 -*-
import json, requests
with open('config.json', encoding='utf-8') as f:
    cfg = json.load(f)
r = requests.get(
    cfg['llm_base_url'] + '/models',
    headers={'Authorization': 'Bearer ' + cfg['llm_api_key']},
    timeout=15,
)
print(r.status_code)
print(r.text[:3000])
