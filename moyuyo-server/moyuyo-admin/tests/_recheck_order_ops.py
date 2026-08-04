# -*- coding: utf-8 -*-
"""复测历史 500 接口：订单拦截 / 改价 / 打印记录 及关键写操作链路（含 HMAC 签名）"""
import json
import hmac
import hashlib
import base64
import secrets
import time
import requests
from pathlib import Path

BASE = 'http://localhost:8080'
S = requests.Session()

# 读取签名密钥
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


# 登录
r = S.post(BASE + '/api/admin/auth/login', json={'email': 'admin@moyuyo.com', 'password': '123456'}, timeout=10)
print('登录:', r.status_code, r.text[:200])
token = r.json().get('data', {}).get('token')
print('token:', (token or '')[:40])
H = {'Authorization': f'Bearer {token}'} if token else {}


def post(url, body):
    h = dict(H)
    h.update(sign('POST', url))
    r = S.post(BASE + '/api/admin' + url, json=body, headers=h, timeout=10)
    print(f'POST {url} -> {r.status_code} {r.text[:200]}')
    return r


def get(url, params=None):
    qs = '?' + '&'.join(f'{k}={v}' for k, v in (params or {}).items()) if params else ''
    h = dict(H)
    h.update(sign('GET', '/api/admin' + url + qs))
    r = S.get(BASE + '/api/admin' + url, params=params, headers=h, timeout=10)
    print(f'GET {url} {params or ""} -> {r.status_code} {r.text[:150]}')
    return r


# 1. 订单拦截创建（先取一个真实订单号）
print('\n===== 1. 订单拦截 =====')
order_id = None
r = get('/orders/list', {'page': 1, 'size': 1})
try:
    d = r.json().get('data') or {}
    items = d.get('records') or d.get('list') or d.get('items') or []
    if items:
        order_id = items[0].get('id')
        print('取到订单ID:', order_id)
except Exception as e:
    print('解析订单列表失败:', e, r.text[:150])
post('/order-ops/intercept/create', {'orderId': order_id or 1, 'interceptType': 'MANUAL', 'reason': '复测拦截', 'operator': 'admin'})

# 2. 订单改价创建
print('\n===== 2. 订单改价 =====')
post('/order-ops/price-modify/create', {'orderId': order_id or 1, 'originalAmount': 100, 'adjustAmount': -10, 'reason': '复测改价', 'operator': 'admin'})

# 3. 订单打印记录
print('\n===== 3. 订单打印记录 =====')
post('/order-ops/print/record', {'orderId': order_id or 1, 'printType': 'PICK', 'templateName': '默认模板', 'paperSize': 'A4', 'operator': 'admin'})

# 4. 列表接口确认数据落库
print('\n===== 4. 列表回查 =====')
get('/order-ops/intercept/list')
get('/order-ops/price-modify/list')
get('/order-ops/print/list')
print('\n完成')
