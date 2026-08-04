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

        # 走 全部 → 待处理 → 进行中 → 已关闭 → 超时
        for tab_text in ['待处理', '进行中', '已关闭', '超时', '全部']:
            try:
                # 找到对应的 tab
                page.locator(f'.tab-switcher-item:has-text("{tab_text}")').first.click()
                time.sleep(1)
            except Exception as e:
                print(f'切换 {tab_text} 异常: {e}')

        # 测试所有工单类型
        for type_val, type_text in [('refund', '退款'), ('complaint', '投诉'), ('consult', '咨询')]:
            try:
                page.locator('select').first.select_option(type_val)
                time.sleep(1)
            except Exception as e:
                print(f'类型 {type_text} 异常: {e}')

        # 测试优先级
        for prio in ['high', 'medium', 'low']:
            try:
                page.locator('select').nth(1).select_option(prio)
                time.sleep(1)
            except Exception as e:
                print(f'优先级 {prio} 异常: {e}')

        # 点击查询
        try:
            page.locator('button:has-text("查询")').first.click()
            time.sleep(2)
        except Exception as e:
            print(f'查询异常: {e}')

        # 翻页
        for btn_text in ['下一页', '3']:
            try:
                page.locator(f'button:has-text("{btn_text}")').first.click()
                time.sleep(1)
            except Exception as e:
                print(f'{btn_text} 异常: {e}')

        # 报告所有 API 响应
        print('\n=== 完整 API 响应记录 ===')
        for r in responses:
            if '/api/' in r['url']:
                marker = '✗' if 400 <= r['status'] < 600 else '✓'
                print(f"{marker} [{r['status']}] {r['method']} {r['url']}")

        # 检查页面错误
        msgs = page.locator('.el-message--error, .el-notification__title, .error-msg').all()
        print('\n=== 页面错误提示 ===')
        for m in msgs:
            try:
                text = m.inner_text().strip()
                if text and len(text) < 500:
                    print(f'提示: {text}')
            except:
                pass

        page.screenshot(path='cs_full_test.png', full_page=True)
        browser.close()

main()
