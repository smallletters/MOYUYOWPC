"""检查空态页面 body 内容"""
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

    PAGES = [
        '/user-profile/180000001', '/settlement-detail/1',
        '/content-review-detail/1', '/push-detail/1', '/complaint-handle/1'
    ]

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context()
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        for path in PAGES:
            page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(3000)
            text = page.evaluate("() => document.body.innerText.trim()")
            print(f'===== {path} =====')
            print(text[:300].replace('\n', ' | '))
            print()
        browser.close()

if __name__ == '__main__':
    main()
