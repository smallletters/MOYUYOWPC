# -*- coding: utf-8 -*-
"""用 API 直接验证 OrderDetail 和 ProductEdit 接口"""
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


def http(method, path, body=None):
    h = {'Authorization': f'Bearer {token}'}
    h.update(sign(method, path))
    if body is not None:
        h['Content-Type'] = 'application/json'
        data = json.dumps(body).encode()
    else:
        data = None
    r = urllib.request.Request(f'{BASE}{path}', headers=h, method=method, data=data)
    try:
        with urllib.request.urlopen(r, timeout=8) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', errors='replace')[:300]


# 登录
body = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(f'{BASE}/api/admin/auth/login', data=body, headers={'Content-Type': 'application/json'}, method='POST')
token = json.loads(urllib.request.urlopen(req, timeout=8).read())['data']['token']
print('[token]', token[:30] + '...')

# 取真实订单ID（注意：响应字段为 list，不是 records）
_, order_data = http('GET', '/api/admin/orders/list?page=1&size=1')
inner = order_data.get('data', {})
if isinstance(inner, list):
    records = inner
else:
    records = inner.get('records') or inner.get('list') or []
order_id = records[0].get('id') if records else None
print('[真实订单ID]', order_id)

# 取真实商品ID
_, prod_data = http('GET', '/api/admin/products/list?page=1&size=1')
inner = prod_data.get('data', {})
if isinstance(inner, list):
    records = inner
else:
    records = inner.get('records') or inner.get('list') or []
prod_id = records[0].get('id') if records else None
print('[真实商品ID]', prod_id)

# 验证 OrderDetail
status, body = http('GET', f'/api/admin/orders/{order_id}')
print(f'\n=== /api/admin/orders/{order_id} ===')
print(f'  HTTP: {status}')
if status == 200:
    keys = list(body.get('data', {}).keys()) if isinstance(body.get('data'), dict) else []
    print(f'  keys (前15): {keys[:15]}')
    print(f'  has items: {"items" in body.get("data", {})}')
    print(f'  has orderNo: {"orderNo" in body.get("data", {})}')
    print(f'  data.size: {len(json.dumps(body.get("data", {})))}')
else:
    print(f'  body: {body}')

# 验证 ProductEdit
status, body = http('GET', f'/api/admin/products/{prod_id}')
print(f'\n=== /api/admin/products/{prod_id} ===')
print(f'  HTTP: {status}')
if status == 200:
    keys = list(body.get('data', {}).keys()) if isinstance(body.get('data'), dict) else []
    print(f'  keys (前20): {keys[:20]}')
    print(f'  data.size: {len(json.dumps(body.get("data", {})))}')
else:
    print(f'  body: {body}')
