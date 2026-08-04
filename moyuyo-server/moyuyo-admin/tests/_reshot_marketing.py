# -*- coding: utf-8 -*-
"""营销页修复后重截图验证"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data,
                                 method='POST', headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        page = b.new_context(viewport={'width': 1440, 'height': 900}).new_page()
        page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        page.goto('http://localhost:5173/admin/marketing', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(1800)
        page.screenshot(path='ui_compare_20260801/impl_marketing.png', full_page=False)
        b.close()
    print('OK')

if __name__ == '__main__':
    main()
