# 阶段3 验证：8 个样式修改页面加载 + 无控制台错误
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

PAGES = [
    ('/order-monitor', '订单监控'),
    ('/sms', '短信管理'),
    ('/cs-sessions', '客服会话'),
    ('/coupon-manage', '优惠券管理'),
    ('/tariff', '关税管理'),
    ('/order-price-modify', '订单改价'),
    ('/users', '用户管理'),
    ('/order-tags', '订单标签'),
]


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        token = json.loads(r.read())['data']['token']

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        for path, name in PAGES:
            page = browser.new_page()
            page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
            console_errors = []
            page.on('console', lambda m: console_errors.append(m.text) if m.type == 'error' else None)
            page.on('pageerror', lambda e: console_errors.append(f'PAGEERROR: {e}'))
            page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(2500)
            body_len = page.evaluate("() => document.body.innerText.length")
            # 检查样式变量是否生效（inline style 里应包含 var(--）
            style_ok = page.evaluate("""() => {
                const els = document.querySelectorAll('[style*="var(--"]');
                return els.length;
            }""")
            status = 'OK' if not console_errors and body_len > 30 else 'FAIL'
            print(f'[{status}] {path} ({name}) body_len={body_len} var样式节点={style_ok}')
            for ce in console_errors[:3]:
                print(f'    console: {ce[:150]}')
            page.close()
        browser.close()


if __name__ == '__main__':
    main()
