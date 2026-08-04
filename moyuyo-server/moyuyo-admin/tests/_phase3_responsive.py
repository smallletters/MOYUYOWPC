# -*- coding: utf-8 -*-
"""阶段3：响应式适配检查（移动端/平板视口）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

ROUTES = ['/dashboard', '/orders', '/products', '/users', '/tariff', '/risk-rule-engine',
          '/settlement', '/cms', '/inventory-transfer', '/batch-import', '/push-manage', '/sensitive-words']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    for vp_name, vp in [('mobile375', {'width': 375, 'height': 812}), ('tablet768', {'width': 768, 'height': 1024})]:
        ctx = b.new_context(viewport=vp)
        page = ctx.new_page()
        page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        for r in ROUTES:
            page.goto(BASE_FE + '/admin' + r, timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(1200)
            m = page.evaluate('()=>{const d=document.documentElement;return {ow:d.scrollWidth,iw:window.innerWidth}}')
            flag = 'OVERFLOW' if m['ow'] > m['iw'] + 2 else 'ok'
            print(f'{vp_name} {r}: {flag} (doc={m["ow"]}/win={m["iw"]})')
        ctx.close()
    b.close()
print('done')
