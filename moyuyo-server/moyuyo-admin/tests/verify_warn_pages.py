# -*- coding: utf-8 -*-
"""用真实ID验证两个 WARN 页面"""
from playwright.sync_api import sync_playwright
import json, urllib.request, hmac, hashlib, base64, secrets, time
from pathlib import Path

BASE = 'http://localhost:8080'
ADMIN = '/admin'
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


# 1) 登录拿 token
body = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(f'{BASE}/api/admin/auth/login', data=body, headers={'Content-Type': 'application/json'}, method='POST')
token = json.loads(urllib.request.urlopen(req, timeout=8).read())['data']['token']
print('[token]', token[:30] + '...')


def get(path):
    h = {'Authorization': f'Bearer {token}'}
    h.update(sign('GET', path))
    r = urllib.request.Request(f'{BASE}{path}', headers=h, method='GET')
    return json.loads(urllib.request.urlopen(r, timeout=8).read().decode())


# 2) 拿真实订单ID
order_data = get('/api/admin/orders/list?page=1&size=1')
records = order_data.get('data', {}).get('records', []) if isinstance(order_data.get('data'), dict) else order_data.get('data', [])
order_id = records[0].get('id') if records else None
print('[真实订单ID]', order_id)

# 3) 拿真实商品ID
prod_data = get('/api/admin/products/list?page=1&size=1')
records = prod_data.get('data', {}).get('records', []) if isinstance(prod_data.get('data'), dict) else prod_data.get('data', [])
prod_id = records[0].get('id') if records else None
print('[真实商品ID]', prod_id)

# 4) Playwright 验证
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context()
    page = ctx.new_page()
    page.goto(f'{BASE}{ADMIN}/login')
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    page.evaluate("t => localStorage.setItem('admin_token', t)", token)
    time.sleep(0.5)

    # OrderDetail 真实ID
    ce1, pe1 = [], []
    page.on('console', lambda m: ce1.append(m.text[:200]) if m.type == 'error' else None)
    page.on('pageerror', lambda e: pe1.append(str(e)[:200]))
    page.goto(f'{BASE}{ADMIN}/orders/{order_id}', wait_until='domcontentloaded', timeout=15000)
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    print(f'=== OrderDetail 真实ID={order_id} ===')
    print('  URL:', page.url)
    print('  console_errors:', ce1)
    print('  page_errors:', pe1)

    # ProductEdit 真实ID
    page2 = ctx.new_page()
    ce2, pe2 = [], []
    page2.on('console', lambda m: ce2.append(m.text[:200]) if m.type == 'error' else None)
    page2.on('pageerror', lambda e: pe2.append(str(e)[:200]))
    page2.goto(f'{BASE}{ADMIN}/products/edit/{prod_id}', wait_until='domcontentloaded', timeout=15000)
    page2.wait_for_load_state('networkidle')
    time.sleep(2)
    print(f'=== ProductEdit 真实ID={prod_id} ===')
    print('  URL:', page2.url)
    print('  console_errors:', ce2)
    print('  page_errors:', pe2)
    browser.close()
