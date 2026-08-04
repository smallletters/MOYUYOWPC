"""验证 PointsManage / ProductAnalysis 修复后无回归"""
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
        for path in ['/points-manage', '/product-analysis']:
            errs = []
            page.on('console', lambda m, e=errs: e.append(m.text) if m.type == 'error' else None)
            page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(2500)
            print(path, 'body_len=', page.evaluate('() => document.body.innerText.length'),
                  'console_err=', len(errs))
            for e in errs[:3]:
                print('   ', e[:120])
        browser.close()

if __name__ == '__main__':
    main()
