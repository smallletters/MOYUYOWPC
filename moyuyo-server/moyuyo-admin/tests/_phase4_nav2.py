# -*- coding: utf-8 -*-
"""阶段4：页面跳转与数据传递验收（修正按钮文本）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

# 跳转链路：列表页 -> 按钮文本 -> 期望 URL 片段
FLOWS = [
    ('/orders', '详情', '/orders/'),
    ('/products', '编辑', '/products/edit/'),
    ('/complaint', '详情', '/complaint-handle'),
    ('/push-manage', '编辑', '/push-detail'),
]

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    console_errs = []
    page.on('console', lambda m: console_errs.append(m.text) if m.type == 'error' else None)
    page.on('pageerror', lambda e: console_errs.append('PAGEERROR: ' + str(e)))
    for route, btn_text, expect in FLOWS:
        try:
            page.goto(BASE_FE + '/admin' + route, timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(1800)
            btn = page.locator(f'button:has-text("{btn_text}")').first
            if not btn.count():
                print(f'{route}: 未找到「{btn_text}」按钮')
                continue
            btn.click(timeout=3000)
            page.wait_for_timeout(2000)
            url = page.url
            ok = expect in url
            print(f'{route} [点击{btn_text}] -> {url} {"OK" if ok else "FAIL"}')
        except Exception as e:
            print(f'{route}: 异常 {str(e)[:80]}')
    print('console errors:', console_errs if console_errs else '无')
    b.close()
print('done')
