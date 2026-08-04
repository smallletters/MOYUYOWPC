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

        # 直接调用 assignTicket API（模拟 转交 按钮）
        # 通过 evaluate 调用
        result = page.evaluate("""
        async () => {
            const { default: api } = await import('/admin/src/api/index.js');
            const { assignTicket } = await import('/admin/src/api/admin.js');
            try {
                const r = await assignTicket(1, { assignee: '195000003' });
                return { ok: true, data: r };
            } catch (e) {
                return { ok: false, error: e.message, status: e.response?.status };
            }
        }
        """)
        print('assignTicket 结果:', result)

        # 也测试 replyTicket
        result2 = page.evaluate("""
        async () => {
            const { replyTicket } = await import('/admin/src/api/admin.js');
            try {
                const r = await replyTicket(1, { content: '测试回复' });
                return { ok: true, data: r };
            } catch (e) {
                return { ok: false, error: e.message, status: e.response?.status };
            }
        }
        """)
        print('replyTicket 结果:', result2)

        # 测试 updateTicketStatus
        result3 = page.evaluate("""
        async () => {
            const { updateTicketStatus } = await import('/admin/src/api/admin.js');
            try {
                const r = await updateTicketStatus(1, { status: 'PROCESSING' });
                return { ok: true, data: r };
            } catch (e) {
                return { ok: false, error: e.message, status: e.response?.status };
            }
        }
        """)
        print('updateTicketStatus 结果:', result3)

        # 测试 getTicketDetail
        result4 = page.evaluate("""
        async () => {
            const { getTicketDetail } = await import('/admin/src/api/admin.js');
            try {
                const r = await getTicketDetail(1);
                return { ok: true, data: r };
            } catch (e) {
                return { ok: false, error: e.message, status: e.response?.status };
            }
        }
        """)
        print('getTicketDetail 结果:', result4)

        # 报告所有 API 响应
        print('\n=== 完整 API 响应记录 ===')
        for r in responses:
            if '/api/' in r['url']:
                marker = '✗' if 400 <= r['status'] < 600 else '✓'
                print(f"{marker} [{r['status']}] {r['method']} {r['url']}")

        browser.close()

main()
