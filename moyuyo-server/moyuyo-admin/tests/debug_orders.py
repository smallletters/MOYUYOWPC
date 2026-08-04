# -*- coding: utf-8 -*-
"""检查订单列表返回字段"""
import json, urllib.request, hmac, hashlib, base64, secrets, time
from pathlib import Path

BASE = 'http://localhost:8080'
ENV = {}
for line in Path(r'D:\MOYUYOWPC\moyuyo-server\.env').read_text(encoding='utf-8').splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    k, v = line.split('=', 1)
    ENV[k.strip()] = v.strip()
SIGN_SECRET = ENV.get('API_SIGN_SECRET', '')


def sign(method, path):
    ts = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{ts}{nonce}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(SIGN_SECRET.encode(), payload.encode(), hashlib.sha256).digest()
    return {
        'X-Sign': base64.b64encode(sig).decode(),
        'X-Timestamp': ts,
        'X-Nonce': nonce,
    }


body = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(f'{BASE}/api/admin/auth/login', data=body, headers={'Content-Type': 'application/json'}, method='POST')
token = json.loads(urllib.request.urlopen(req, timeout=8).read())['data']['token']

h = {'Authorization': f'Bearer {token}'}
h.update(sign('GET', '/api/admin/orders/list?page=1&size=2'))
r = urllib.request.Request(f'{BASE}/api/admin/orders/list?page=1&size=2', headers=h, method='GET')
data = json.loads(urllib.request.urlopen(r, timeout=8).read().decode())
print('[orders.list response]')
print(json.dumps(data, ensure_ascii=False, indent=2)[:2500])
