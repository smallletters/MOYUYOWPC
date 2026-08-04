# -*- coding: utf-8 -*-
"""验证全局标题字号统一生效"""
import json, urllib.request
from playwright.sync_api import sync_playwright

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    p = ctx.new_page()
    p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    for r in ['/orders', '/refund', '/marketing', '/tariff', '/dashboard']:
        p.goto(f'http://localhost:5173/admin{r}', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1800)
        fs = p.evaluate("""() => { const h = document.querySelector('.page-header h2, .page-title-area h1, .page-header-left h1'); return h ? getComputedStyle(h).fontSize : 'N/A' }""")
        print(f'{r}: 标题字号={fs}')
    b.close()
