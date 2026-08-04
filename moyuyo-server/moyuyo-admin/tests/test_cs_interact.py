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

        # 切换 "待处理" Tab - 触发 405
        try:
            page.locator('button:has-text("待处理")').first.click()
            time.sleep(2)
        except Exception as e:
            print(f'切换失败: {e}')

        # 切换 "进行中" Tab
        try:
            page.locator('button:has-text("进行中")').first.click()
            time.sleep(2)
        except Exception as e:
            print(f'切换失败: {e}')

        # 切换 "已关闭" Tab
        try:
            page.locator('button:has-text("已关闭")').first.click()
            time.sleep(2)
        except Exception as e:
            print(f'切换失败: {e}')

        # 选择工单类型
        try:
            page.locator('select').first.select_option('refund')
            time.sleep(2)
        except Exception as e:
            print(f'类型选择失败: {e}')

        # 点击查询按钮
        try:
            page.locator('button:has-text("查询")').first.click()
            time.sleep(2)
        except Exception as e:
            print(f'查询失败: {e}')

        # 报告
        print('\n=== 所有 API 响应 ===')
        for r in responses:
            if '/api/' in r['url']:
                print(f"[{r['status']}] {r['method']} {r['url']}")

        # 检查页面错误提示
        print('\n=== 页面错误信息 ===')
        msgs = page.locator('.el-message, .error-msg, .el-notification').all()
        for m in msgs:
            try:
                text = m.inner_text().strip()
                if text and len(text) < 300:
                    print(f'提示: {text}')
            except:
                pass

        page.screenshot(path='cs_after_clicks.png', full_page=True)
        print('\n截图: cs_after_clicks.png')

        browser.close()

main()
