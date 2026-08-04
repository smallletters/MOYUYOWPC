# -*- coding: utf-8 -*-
"""检查列表页数据行与全部按钮文本"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

ROUTES = ['/orders', '/products', '/complaint', '/push-manage', '/reviews', '/content-review', '/cs']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    for route in ROUTES:
        page.goto(BASE_FE + '/admin' + route, timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2000)
        rows = page.locator('.el-table__body-wrapper tbody tr').count()
        btns = page.locator('button').all_text_contents()
        btns = [x.strip() for x in btns if x.strip()]
        print(f'{route}: 行数={rows} 按钮={list(dict.fromkeys(btns))[:10]}')
    b.close()
print('done')
