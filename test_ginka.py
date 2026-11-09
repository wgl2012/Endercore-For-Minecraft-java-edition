# -*- coding: utf-8 -*-
import requests
base = "https://api.ginka.cloud/v1"
# Try to list models without auth first
r = requests.get(base + "/models", timeout=10)
print(r.status_code)
print(r.text[:2000])
