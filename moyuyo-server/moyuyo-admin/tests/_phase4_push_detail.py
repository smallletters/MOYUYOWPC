# -*- coding: utf-8 -*-
"""验证 push-manage 详情按钮跳转"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    page.goto(BASE_FE + '/admin/push-manage', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1800)
    btn = page.locator('button:has-text("详情")').first
    print('详情按钮数:', btn.count())
    btn.click(timeout=3000)
    page.wait_for_timeout(2000)
    print('URL:', page.url)
    b.close()
