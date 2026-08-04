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

        # 检查页面中的错误
        page_text = page.locator('body').inner_text()
        if '错误' in page_text or 'Error' in page_text or '失败' in page_text:
            print('页面包含错误信息!')
            print('---页面文本---')
            print(page_text[:2000])
        else:
            print('页面无错误信息')

        # 表格行数
        rows = page.locator('tbody tr').all()
        print(f'\n表格行数: {len(rows)}')

        # 截屏
        page.screenshot(path='cs_page_now.png', full_page=True)
        print('截图保存: cs_page_now.png')

        browser.close()

main()
