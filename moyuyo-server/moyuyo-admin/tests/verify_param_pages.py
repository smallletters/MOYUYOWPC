# -*- coding: utf-8 -*-
"""使用真实 ID 验证 /orders/:id 和 /products/edit/:id"""
import json
import urllib.request
import urllib.error
from playwright.sync_api import sync_playwright
import time
import sys

BASE = 'http://localhost:8080'
ADMIN_BASE = f'{BASE}/admin'

def get_token():
    url = f'{BASE}/api/admin/auth/login'
    data = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST', headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=8) as r:
        payload = json.loads(r.read().decode('utf-8'))
        return payload['data']['token']

def get_first_id(token, path):
    """从列表接口拿第一条真实 ID"""
    headers = {"Authorization": f"Bearer {token}"}
    req = urllib.request.Request(f"{BASE}{path}", headers=headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            payload = json.loads(r.read().decode('utf-8'))
            data = payload.get('data') or {}
            # 兼容 {records:[...]} 或 [...] 两种格式
            items = data.get('records') if isinstance(data, dict) else data
            if isinstance(items, list) and items:
                first = items[0]
                if isinstance(first, dict):
                    return first.get('id') or first.get('orderId') or first.get('productId')
    except Exception as e:
        print(f"  [err] {path}: {e}")
    return None

def test_page_with_id(page, path_template, real_id, label):
    """用真实 ID 测试页面"""
    full_path = path_template.replace(':id', str(real_id))
    url = f"{ADMIN_BASE}/{full_path}"
    errors = []
    page_errors = []

    def on_console(msg):
        if msg.type == 'error':
            errors.append(msg.text[:200])

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
        'console_errors': errors[:3], 'page_errors': page_errors[:3]
    }


def main():
    print("=" * 80)
    print("【验证参数化页面：使用真实 ID】")
    print("=" * 80)

    token = get_token()
    print(f"[token] {token[:20]}...\n")

    order_id = get_first_id(token, '/api/admin/orders/list')
    product_id = get_first_id(token, '/api/admin/products/list')
    print(f"[真实 ID] orders={order_id}  products={product_id}\n")

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

        results = []
        if order_id:
            r = test_page_with_id(page, 'orders/:id', order_id, 'OrderDetail')
            results.append(r)
        else:
            print("[跳过] 订单列表为空，无可用 ID")

        if product_id:
            r = test_page_with_id(page, 'products/edit/:id', product_id, 'ProductEdit')
            results.append(r)
        else:
            print("[跳过] 商品列表为空，无可用 ID")

        browser.close()

    print("=" * 80)
    print("【结果】")
    for r in results:
        print(f"\n{r['label']} ({r['url']})")
        print(f"  status: {r['status']}  btn: {r.get('btn', 0)}  content: {r.get('has_content')}")
        if r.get('page_errors'):
            print(f"  page_errors: {r['page_errors']}")
        if r.get('console_errors'):
            print(f"  console_errors: {r['console_errors']}")

    ok = all(r['status'] == 'OK' for r in results)
    print(f"\n{'=' * 80}")
    print(f"【结论】 {'全部 OK' if ok else '存在问题'}")
    print(f"{'=' * 80}")
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
