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

        # 截屏看页面内容
        page.screenshot(path='cs_page_full.png', full_page=True)

        # 打印所有 API 响应
        print('=== 所有 API 响应 ===')
        for r in responses:
            if '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")

        # 检查页面上的所有错误信息
        error_elements = page.locator('.el-message--error, .error-msg, .error').all()
        for el in error_elements:
            try:
                text = el.inner_text().strip()
                if text:
                    print(f'页面错误提示: {text[:200]}')
            except:
                pass

        # 检查 tbody 行
        rows = page.locator('tbody tr').all()
        print(f'\n表格行数: {len(rows)}')
        if rows:
            for i, row in enumerate(rows[:3]):
                try:
                    print(f'  行{i}: {row.inner_text()[:200]}')
                except:
                    pass

        browser.close()

main()
