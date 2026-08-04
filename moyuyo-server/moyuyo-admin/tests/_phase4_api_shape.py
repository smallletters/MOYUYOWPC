# -*- coding: utf-8 -*-
"""捕获关键列表页 API 响应格式"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

ROUTES = ['/orders', '/products', '/complaint', '/push-manage', '/users', '/content-review']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    for route in ROUTES:
        captured = []
        def on_response(resp):
            if '/api/admin/' in resp.url and resp.request.method == 'GET':
                try:
                    body = resp.json()
                    d = body.get('data') if isinstance(body, dict) else None
                    shape = type(d).__name__
                    if isinstance(d, dict):
                        shape += ' keys=' + str(list(d.keys()))[:6]
                        for k in ('records', 'list', 'data', 'items'):
                            if isinstance(d.get(k), list):
                                shape += f' {k}:{len(d[k])}'
                    elif isinstance(d, list):
                        shape = f'list(len={len(d)})'
                    captured.append(resp.url.split('/api/admin/')[-1][:60] + ' -> ' + shape)
                except Exception:
                    pass
        page.on('response', on_response)
        page.goto(BASE_FE + '/admin' + route, timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        print(f'=== {route} ===')
        for c in captured:
            print('  ', c)
    b.close()
print('done')
