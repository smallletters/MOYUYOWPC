"""用真实数据 id 复测参数页 + 关键页面深度检查"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        p = json.loads(r.read())
    token = p['data']['token']

    # 用真实 id 的页面
    PAGES = [
        ('/orders/186000001', '订单详情(真实)'),
        ('/products/edit/183000001', '编辑商品(真实)'),
        ('/user-profile/180000001', '用户画像(真实)'),
        ('/settlement-detail/1', '结算详情'),
        ('/content-review-detail/1', '内容审核详情'),
        ('/push-detail/1', '推送详情'),
        ('/complaint-handle/1', '投诉处理详情'),
    ]

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context()
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        for path, name in PAGES:
            console_errors = []
            failed_reqs = []

            def on_console(msg):
                if msg.type == 'error':
                    console_errors.append(msg.text)
            def on_pageerror(exc):
                console_errors.append(f'PAGEERROR: {exc}')
            def on_response(resp):
                if resp.status >= 400:
                    failed_reqs.append(f'{resp.status} {resp.url}')
            try:
                page.remove_listener('console', on_console)
                page.remove_listener('pageerror', on_pageerror)
                page.remove_listener('response', on_response)
            except Exception:
                pass
            page.on('console', on_console)
            page.on('pageerror', on_pageerror)
            page.on('response', on_response)

            try:
                page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(3000)
                body_len = page.evaluate("() => document.body.innerText.length")
                status = 'OK' if not console_errors else 'CONSOLE_ERR'
                print(f"[{status}] {path} ({name}) body_len={body_len}")
                for ce in console_errors[:5]:
                    print(f"    console: {ce[:180]}")
                for fr in failed_reqs[:5]:
                    print(f"    http: {fr[:180]}")
            except Exception as e:
                print(f"[LOAD_FAIL] {path} ({name}): {e}")

        browser.close()

if __name__ == '__main__':
    main()
