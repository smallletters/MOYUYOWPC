# -*- coding: utf-8 -*-
"""阶段3：核心页面截图（用于 taste 审美评估与设计稿对比）"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
ROUTES = [
    ('/dashboard', 'dashboard'),
    ('/orders', 'orders'),
    ('/products', 'products'),
    ('/users', 'users'),
    ('/marketing', 'marketing'),
    ('/refund', 'refund'),
]


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        for route, name in ROUTES:
            try:
                p.goto(f'http://localhost:5173/admin{route}', timeout=20000, wait_until='domcontentloaded')
                p.wait_for_timeout(2000)
                p.screenshot(path=f'phase3_shot_{name}.png', full_page=True)
                print(f'OK {name} len={len(p.evaluate("()=>document.body.innerText"))}')
            except Exception as e:
                print(f'ERR {name}: {str(e)[:80]}')
        b.close()


if __name__ == '__main__':
    main()
