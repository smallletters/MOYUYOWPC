# -*- coding: utf-8 -*-
"""抓 ProductEdit 404 资源 + 验证 OrderList 渲染"""
import json
import urllib.request
from playwright.sync_api import sync_playwright
import time

BASE = 'http://localhost:8080'
ADMIN_BASE = f'{BASE}/admin'

def get_token():
    data = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode('utf-8')
    req = urllib.request.Request(f'{BASE}/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=8) as r:
        return json.loads(r.read().decode('utf-8'))['data']['token']

def main():
    token = get_token()
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        ctx = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = ctx.new_page()

        # 抓 404 资源
        failed_404 = []
        failed_other = []
        def on_response(resp):
            if resp.status == 404:
                failed_404.append(f"[404] {resp.url}")
        page.on('response', on_response)

        # 注入 token
        page.goto(f"{ADMIN_BASE}/login")
        time.sleep(0.5)
        page.evaluate(f"localStorage.setItem('admin_token', '{token}')")

        # 1. 加载 ProductEdit
        print("=== 加载 ProductEdit (id=2080199390045450241) ===")
        page.goto(f"{ADMIN_BASE}/products/edit/2080199390045450241", wait_until='networkidle', timeout=15000)
        time.sleep(2)
        for url in failed_404:
            print(url)
        failed_404.clear()

        # 2. 加载 OrderList 检查实际渲染
        print("\n=== 加载 OrderList 检查渲染 ===")
        page.goto(f"{ADMIN_BASE}/orders", wait_until='networkidle', timeout=15000)
        time.sleep(2)
        # 取表格中前 3 行的实际内容
        rows = page.locator('table.data-table tbody tr').all()
        print(f"渲染订单行数: {len(rows)}")
        for i, row in enumerate(rows[:3]):
            cells = row.locator('td').all_text_contents()
            print(f"  行 {i+1}: {cells}")

        # 3. 取 console 日志中所有失败请求
        print(f"\n=== 累计 404 ({len(failed_404)}) ===")
        for url in failed_404:
            print(url)

        browser.close()

if __name__ == '__main__':
    main()
