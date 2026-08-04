"""捕获单个页面完整 console 错误，定位 vite deps 问题"""
import json
import urllib.request
from playwright.sync_api import sync_playwright

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST',
                             headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    p = ctx.new_page()
    p.add_init_script("localStorage.setItem('admin_token', '%s')" % token)
    errs = []
    p.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
    p.on('pageerror', lambda e: errs.append('PAGEERROR: ' + str(e)))
    p.goto('http://localhost:5174/admin/review-manage', wait_until='networkidle', timeout=15000)
    p.wait_for_timeout(1500)
    for e in errs:
        print('FULL_ERR:', e)
    if not errs:
        print('NO ERRORS')
    b.close()
