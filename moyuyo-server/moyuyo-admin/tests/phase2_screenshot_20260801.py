"""阶段2修复页面截图 + 布局冒烟验证（供审美评估与验收使用）
检查项：console 错误、横向溢出、页面加载状态
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import os

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
OUT_DIR = 'D:/MOYUYOWPC/moyuyo-server/moyuyo-admin/tests/screenshots_phase2_20260801'

# 本次阶段2修复/重构的页面
PAGES = [
    ('/order-export', 'order-export'),
    ('/content-review-detail/1', 'content-review-detail'),
    ('/product-report', 'product-report'),
    ('/marketing-effect', 'marketing-effect'),
    ('/product-analysis', 'product-analysis'),
    ('/user-profile', 'user-profile'),
    ('/funnel', 'funnel'),
    ('/settlement', 'settlement'),
    ('/satisfaction', 'satisfaction'),
    ('/search-analysis', 'search-analysis'),
    ('/overseas-warehouse', 'overseas-warehouse'),
    ('/risk-control', 'risk-control'),
    ('/price-manage', 'price-manage'),
    ('/system-config', 'system-config'),
    ('/sms', 'sms'),
    ('/order-monitor', 'order-monitor'),
    ('/order-print', 'order-print'),
    ('/push-detail', 'push-detail'),
    ('/complaint', 'complaint'),
    ('/clearance', 'clearance'),
    ('/customs', 'customs'),
    ('/carrier-compare', 'carrier-compare'),
    ('/inventory', 'inventory'),
    ('/split-package', 'split-package'),
    ('/warehouse-manage', 'warehouse-manage'),
    ('/realtime-screen', 'realtime-screen'),
    ('/gdpr', 'gdpr'),
    ('/rfm', 'rfm'),
    ('/review-manage', 'review-manage'),
    ('/live-manage', 'live-manage'),
    ('/complaint-handle', 'complaint-handle'),
    ('/campaign', 'campaign'),
]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        token = json.loads(r.read())['data']['token']

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        summary = []
        console_errors = []

        def on_console(msg):
            if msg.type == 'error':
                console_errors.append(msg.text)

        page.on('console', on_console)

        for route, tag in PAGES:
            try:
                page.goto(f'{BASE_FE}/admin{route}', wait_until='networkidle', timeout=15000)
                page.wait_for_timeout(1200)
                # 溢出检测
                overflow = page.evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth")
                page.screenshot(path=f'{OUT_DIR}/{tag}.png', full_page=False)
                summary.append({'route': route, 'status': 'OK', 'overflow': overflow})
                print(f'OK  {route}  overflow={overflow}')
            except Exception as e:
                summary.append({'route': route, 'status': 'ERR', 'error': str(e)})
                print(f'ERR {route}  {e}')

        # 汇总控制台错误
        errors_by_page = {}
        for err in console_errors:
            key = err[:80]
            errors_by_page[key] = errors_by_page.get(key, 0) + 1

        with open(f'{OUT_DIR}/_summary.json', 'w', encoding='utf-8') as f:
            json.dump({
                'pages': summary,
                'console_errors': errors_by_page,
                'total_console_errors': len(console_errors)
            }, f, ensure_ascii=False, indent=2)

        print(f'\n共 {len(summary)} 页，控制台错误 {len(console_errors)} 条')
        for k, v in errors_by_page.items():
            print(f'  [{v}x] {k}')
        browser.close()


if __name__ == '__main__':
    main()
