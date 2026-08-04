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

        console_msgs = []
        def on_console(m):
            console_msgs.append({'type': m.type, 'text': m.text})
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

        # 访问客服管理页面
        page.goto('http://localhost:5173/admin/cs', wait_until='domcontentloaded', timeout=15000)
        page.wait_for_load_state('networkidle', timeout=10000)
        time.sleep(3)

        # 切换不同的 Tab 触发不同请求
        tabs = ['待处理', '进行中', '已关闭', '超时', '全部']
        for t in tabs:
            try:
                page.locator(f'.tab-switcher-item:has-text("{t}")').first.click()
                time.sleep(1)
            except Exception as e:
                print(f'切换 {t} 失败: {e}')

        # 找到 "转交" 链接
        try:
            # 等待出现转交链接
            page.wait_for_selector('text=转交', timeout=5000)
            links = page.locator('text=转交').all()
            print(f'\n找到 {len(links)} 个 "转交" 链接')
            if links:
                # 点击第一个
                links[0].click()
                page.wait_for_timeout(1000)
                prompt_input = page.locator('.el-message-box__input input').first
                if prompt_input.count() > 0:
                    prompt_input.fill('195000003')
                    page.locator('.el-message-box__btns button.el-button--primary').click()
                    page.wait_for_timeout(2000)
                    print('已点击确认')
        except Exception as e:
            print(f'\n转交测试异常: {e}')

        # 报告
        print('\n=== 4xx/5xx 响应 ===')
        for r in responses:
            if 400 <= r['status'] < 600 and '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")

        # 报告 console
        errors = [m for m in console_msgs if m['type'] in ('error', 'warning')]
        if errors:
            print('\n=== Console 错误/警告 ===')
            for m in errors:
                print(f"[{m['type']}] {m['text'][:400]}")

        # 报告 page errors
        if page_errors:
            print('\n=== 页面错误 ===')
            for e in page_errors:
                print(e[:500])

        browser.close()

main()
