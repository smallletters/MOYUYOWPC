# -*- coding: utf-8 -*-
"""阶段4-3 回归测试：取真实订单ID + 测 OrderDetail 页面"""
import json
import sys
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import secrets
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:8080'
ADMIN_BASE = f'{BASE}/admin'

# 加载签名密钥
ENV = {}
env_path = Path(r"D:\MOYUYOWPC\moyuyo-server\.env")
if env_path.exists():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        ENV[k.strip()] = v.strip()
SIGN_SECRET = ENV.get("API_SIGN_SECRET", "")

def make_sign(method, path):
    timestamp = str(int(time.time() * 1000))
    nonce = secrets.token_hex(8)
    body = ""
    if method == "GET" and "?" in path:
        path, qs = path.split("?", 1)
        body = qs
    sign_str = f"{method}\n{path}\n{body}\n{timestamp}\n{nonce}"
    sig = hmac.new(SIGN_SECRET.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    return {
        "X-Sign": sig,
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
    }

def login():
    url = f'{BASE}/api/admin/auth/login'
    body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode('utf-8')
    headers = make_sign("POST", "/api/admin/auth/login")
    headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, method='POST', headers=headers)
    with urllib.request.urlopen(req, timeout=8) as r:
        payload = json.loads(r.read().decode('utf-8'))
        return payload['data']['token']

def get_first_order_id(token):
    headers = {"Authorization": f"Bearer {token}"}
    qs = "page=1&size=5"
    full_path = f"/api/admin/orders/list?{qs}"
    headers.update(make_sign("GET", full_path))
    url = f"{BASE}{full_path}"
    req = urllib.request.Request(url, headers=headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            payload = json.loads(r.read().decode('utf-8'))
            data = payload.get('data') or {}
            items = data.get('records') or data.get('list') or data.get('rows') or data
            if isinstance(items, list) and items:
                first = items[0]
                if isinstance(first, dict):
                    return first.get('id') or first.get('orderId')
            return None
    except urllib.error.HTTPError as e:
        # 签名/路径错误时打印，便于诊断
        try:
            err_body = e.read().decode('utf-8', errors='replace')[:200]
        except Exception:
            err_body = ''
        print(f"  [err {e.code}] {full_path}: {err_body}")
        return None
    except Exception as e:
        print(f"  [err] {e}")
        return None

def test_page_with_id(page, path_template, real_id, label):
    full_path = path_template.replace(':id', str(real_id))
    url = f"{ADMIN_BASE}/{full_path}"
    page_errors = []
    console_errors = []

    def on_console(msg):
        if msg.type == 'error':
            console_errors.append(msg.text[:200])

    def on_pageerror(err):
        page_errors.append(str(err)[:200])

    page.on('console', on_console)
    page.on('pageerror', on_pageerror)

    try:
        page.goto(url, wait_until='domcontentloaded', timeout=15000)
    except Exception as e:
        page.remove_listener('console', on_console)
        page.remove_listener('pageerror', on_pageerror)
        return {'label': label, 'url': url, 'status': 'TIMEOUT', 'error': str(e)[:200]}

    try:
        page.wait_for_load_state('networkidle', timeout=8000)
    except Exception:
        pass
    time.sleep(1.5)

    btn = page.locator('button:visible').count()
    has_content = page.locator('button, table, .el-table, form, .card, .el-card').count() > 0
    page.remove_listener('console', on_console)
    page.remove_listener('pageerror', on_pageerror)

    status = 'OK' if has_content and not page_errors else ('WARN' if has_content else 'FAIL')
    return {
        'label': label, 'url': page.url, 'status': status,
        'btn': btn, 'has_content': has_content,
        'console_errors': console_errors[:3], 'page_errors': page_errors[:3]
    }

def main():
    print("=" * 80)
    print("【阶段4-3 回归测试：取真实订单ID + 验证 OrderDetail】")
    print("=" * 80)

    token = login()
    print(f"[token] {token[:20]}...")

    order_id = get_first_order_id(token)
    print(f"[真实订单ID] {order_id}")

    if not order_id:
        print("[跳过] 无订单数据")
        return 1

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        ctx = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = ctx.new_page()

        # 注入 token
        page.goto(f"{ADMIN_BASE}/login")
        time.sleep(0.5)
        page.evaluate(f"localStorage.setItem('admin_token', '{token}')")
        time.sleep(0.5)
        page.goto(f"{ADMIN_BASE}/dashboard")
        time.sleep(2)

        r = test_page_with_id(page, 'orders/:id', order_id, 'OrderDetail')
        browser.close()

    print("=" * 80)
    print("【结果】")
    print(f"\n{r['label']} ({r['url']})")
    print(f"  status: {r['status']}  btn: {r.get('btn', 0)}  content: {r.get('has_content')}")
    if r.get('page_errors'):
        print(f"  page_errors: {r['page_errors']}")
    if r.get('console_errors'):
        print(f"  console_errors: {r['console_errors']}")

    ok = r['status'] == 'OK'
    print(f"\n{'=' * 80}")
    print(f"【结论】 {'OK' if ok else 'WARN/FAIL'}")
    print(f"{'=' * 80}")
    return 0 if ok else 1

if __name__ == '__main__':
    sys.exit(main())
