from playwright.sync_api import sync_playwright
import time

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = context.new_page()

        responses = []
        def on_response(r):
            try:
                responses.append({'status': r.status, 'url': r.url, 'method': r.request.method})
            except Exception:
                pass
        page.on('response', on_response)

        console_errors = []
        def on_console(m):
            if m.type == 'error':
                console_errors.append(m.text)
        page.on('console', on_console)

        page_errors = []
        def on_pageerror(e):
            page_errors.append(str(e))
        page.on('pageerror', on_pageerror)

        # 登录
        page.goto('http://localhost:5173/admin/login', wait_until='networkidle', timeout=15000)
        page.locator('input[type="email"]').fill('admin@moyuyo.com')
        page.locator('input[type="password"]').fill('123456')
        page.locator('button.login-btn').click()
        page.wait_for_url('**/dashboard', timeout=10000)
        print('已登录:', page.url)

        # 访问客服管理页面
        page.goto('http://localhost:5173/admin/cs', wait_until='domcontentloaded', timeout=15000)
        page.wait_for_load_state('networkidle', timeout=10000)
        time.sleep(3)

        # 全部 API 响应
        print('\n--- 全部 API 响应 ---')
        for r in responses:
            if '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")

        if console_errors:
            print('\n--- Console 错误 ---')
            for c in console_errors:
                print(c[:500])

        if page_errors:
            print('\n--- 页面错误 ---')
            for e in page_errors:
                print(e[:500])

        # 检查页面 DOM 中是否有 "转交"
        html_snippet = page.content()[:5000]
        print('\n--- 页面 HTML 片段 ---')
        print(html_snippet)

        browser.close()

main()
