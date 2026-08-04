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
        time.sleep(2)

        # 4xx/5xx 报告
        print('\n--- 4xx/5xx 响应 ---')
        found_405 = False
        for r in responses:
            if 400 <= r['status'] < 600 and '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")
                if r['status'] == 405:
                    found_405 = True

        if console_errors:
            print('\n--- Console 错误 ---')
            for c in console_errors:
                print(c[:300])

        if page_errors:
            print('\n--- 页面错误 ---')
            for e in page_errors:
                print(e[:300])

        if not found_405:
            print('\n没有 405 错误')

        await_path = page.screenshot(path='cs_page.png', full_page=True)

        # 点击"转交"按钮
        print('\n--- 测试转交按钮 ---')
        try:
            page.locator('text=转交').first.click()
            page.wait_for_timeout(1000)
            prompt_input = page.locator('.el-message-box__input input, .el-message-box input').first
            if prompt_input.count() > 0:
                prompt_input.fill('1')
                page.locator('.el-message-box__btns button.el-button--primary').click()
                page.wait_for_timeout(2000)
                print('转交流程完成')
            else:
                print('未找到 prompt 输入框')
        except Exception as e:
            print(f'转交测试异常: {e}')

        print('\n--- 点击转交后的网络错误 ---')
        for r in responses[-30:]:
            if 400 <= r['status'] < 600 and '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")

        browser.close()

main()
