"""调试订单详情跳转"""
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
        token = json.loads(r.read())['data']['token']

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        page = browser.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        errs = []
        page.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
        page.goto(f'{BASE_FE}/admin/orders', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        # 列出第一行所有按钮
        btns = page.locator('tbody tr').first.locator('button').all_inner_texts()
        print('第一行按钮:', [b.strip() for b in btns])
        # 点击详情
        page.locator('tbody tr button').filter(has_text='详情').first.click(timeout=5000)
        page.wait_for_timeout(2500)
        print('点击后 URL:', page.url)
        print('console:', [e[:150] for e in errs[:5]])
        body = page.evaluate("() => document.body.innerText")
        print('页面含"订单详情":', '订单详情' in body)
        browser.close()

if __name__ == '__main__':
    main()
