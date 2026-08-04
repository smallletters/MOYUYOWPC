"""阶段3 截图对比：核心页面实现 vs 设计稿"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import os

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
DESIGN_DIR = 'D:/MOYUYOWPC/APPdocs/admin'
OUT_DIR = 'D:/MOYUYOWPC/修复交付物/截图对比/阶段3'

PAGES = [
    ('/dashboard', 'admin-dashboard.html', 'dashboard'),
    ('/orders', 'admin-orders.html', 'orders'),
    ('/products', 'admin-products.html', 'products'),
    ('/users', 'admin-users.html', 'users'),
    ('/marketing', 'admin-marketing.html', 'marketing'),
    ('/finance', 'admin-finance.html', 'finance'),
    ('/login', 'admin-login.html', 'login'),
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

        # 1. 实现页面截图
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        for path, design, tag in PAGES:
            try:
                if path == '/login':
                    page.goto(f'{BASE_FE}/admin/login', timeout=20000, wait_until='domcontentloaded')
                else:
                    page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(3000)
                page.screenshot(path=f'{OUT_DIR}/{tag}_impl.png', full_page=False)
                print(f'实现页已截图: {tag}_impl.png')
            except Exception as e:
                print(f'实现页截图失败 {path}: {e}')

        # 2. 设计稿截图（直接打开本地 HTML）
        for path, design, tag in PAGES:
            try:
                page.goto(f'file:///{DESIGN_DIR}/{design}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(2500)
                page.screenshot(path=f'{OUT_DIR}/{tag}_design.png', full_page=False)
                print(f'设计稿已截图: {tag}_design.png')
            except Exception as e:
                print(f'设计稿截图失败 {design}: {e}')

        browser.close()
    print('完成')

if __name__ == '__main__':
    main()
