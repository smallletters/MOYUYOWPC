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
        time.sleep(2)

        # 输入搜索词
        try:
            page.locator('input[placeholder*="工单"]').fill('TKT')
            time.sleep(2)
        except Exception as e:
            print(f'搜索异常: {e}')

        # 点击重置
        try:
            page.locator('button:has-text("重置")').first.click()
            time.sleep(1)
        except Exception as e:
            print(f'重置异常: {e}')

        # 检查表格中是否有行，如果有则点击"转交"按钮
        rows = page.locator('tbody tr').all()
        print(f'表格行数: {len(rows)}')
        for i, row in enumerate(rows):
            try:
                txt = row.inner_text()
                if txt:
                    print(f'  行{i}: {txt[:100]}')
            except:
                pass

        # 报告所有 API 响应
        print('\n=== 完整 API 响应记录 ===')
        for r in responses:
            if '/api/' in r['url']:
                marker = '✗' if 400 <= r['status'] < 600 else '✓'
                print(f"{marker} [{r['status']}] {r['method']} {r['url']}")

        browser.close()

main()
