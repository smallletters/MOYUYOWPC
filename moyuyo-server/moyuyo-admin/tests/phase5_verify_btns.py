"""精确验证 orders/push-manage 按钮"""
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
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        # 1. /orders 搜索按钮
        page.goto(f'{BASE_FE}/admin/orders', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        btns = page.locator('button').all_inner_texts()
        print('--- /orders 所有按钮文本 ---')
        print([b.strip() for b in btns if b.strip()][:20])
        # 点击搜索按钮（.btn-primary）
        try:
            page.locator('button.btn-primary').first.click(timeout=3000)
            page.wait_for_timeout(1500)
            print('点击 .btn-primary 成功，页面 body 长度变化:')
        except Exception as e:
            print(f'.btn-primary 点击失败: {e}')

        # 2. /push-manage 按钮
        page.goto(f'{BASE_FE}/admin/push-manage', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        btns2 = page.locator('button').all_inner_texts()
        print('--- /push-manage 所有按钮文本 ---')
        print([b.strip() for b in btns2 if b.strip()][:20])

        # 3. /ab-test 按钮 disabled 状态
        page.goto(f'{BASE_FE}/admin/ab-test', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        ab_btns = page.locator('button').evaluate_all("els => els.map(e => ({text: e.innerText.trim(), disabled: e.disabled}))")
        print('--- /ab-test 按钮 ---')
        print([b for b in ab_btns if b['text']][:10])

        browser.close()

if __name__ == '__main__':
    main()
