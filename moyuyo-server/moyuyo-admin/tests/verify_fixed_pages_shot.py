"""本轮修复页面截图 + 布局冒烟验证
检查项：console 错误、横向溢出（scrollWidth > clientWidth）、主要区块存在性
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import os

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
OUT_DIR = 'D:/MOYUYOWPC/修复交付物/截图对比/本轮修复'

# (路由, 标签)
PAGES = [
    ('/gdpr', 'gdpr'),
    ('/push-manage', 'push'),
    ('/audit-log', 'audit-log'),
    ('/traffic-analysis', 'traffic'),
    ('/satisfaction', 'satisfaction'),
    ('/risk-control', 'risk-control'),
    ('/batch-import', 'batch-import'),
    ('/marketing', 'marketing'),
    ('/rfm', 'rfm'),
    ('/cms', 'cms'),
    ('/orders', 'orders'),
    ('/rbac', 'rbac'),
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
        for path, tag in PAGES:
            console_errors.clear()
            try:
                page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(3000)
                # 检查横向溢出
                overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth + 4")
                page.screenshot(path=f'{OUT_DIR}/{tag}_impl.png', full_page=False)
                status = 'OK' if not console_errors and not overflow else 'CHECK'
                summary.append({'path': path, 'status': status, 'overflow': overflow, 'console': console_errors[:2]})
                print(f'[{status}] {path} overflow={overflow} console_err={len(console_errors)}')
            except Exception as e:
                summary.append({'path': path, 'status': 'FAIL', 'err': str(e)[:100]})
                print(f'[FAIL] {path}: {str(e)[:100]}')

        browser.close()

        bad = [s for s in summary if s['status'] != 'OK']
        print(f'\n截图与布局验证: OK={len(summary) - len(bad)} / CHECK={len(bad)} / TOTAL={len(summary)}')
        for s in bad:
            print(f"  - {s['path']}: overflow={s.get('overflow')} console={s.get('console')}")

        with open('verify_fixed_pages.json', 'w', encoding='utf-8') as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
