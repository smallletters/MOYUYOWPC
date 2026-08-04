# -*- coding: utf-8 -*-
"""精确记录浏览器发出的 /risk/rules POST 请求"""
from playwright.sync_api import sync_playwright
import json, urllib.request, time

BASE_FE = 'http://localhost:5173'
data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")

    def on_request(r):
        if '/risk/rules' in r.url and r.method == 'POST':
            print(f'[REQ {time.time():.4f}] POST /risk/rules body={r.post_data}')

    def on_response(r):
        if '/risk/rules' in r.url:
            try:
                print(f'[RES {time.time():.4f}] {r.status} {r.json().get("message")}')
            except Exception:
                print(f'[RES {time.time():.4f}] {r.status} (no-json)')

    page.on('request', on_request)
    page.on('response', on_response)
    page.goto(BASE_FE + '/admin/risk-rule-engine', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    page.locator('button:has-text("新建规则")').first.click(timeout=3000)
    page.wait_for_timeout(800)
    form = page.locator('.el-dialog:visible')
    form.locator('input[placeholder*="输入规则名称"]').fill('验收规则C')
    form.locator('textarea').fill('单日下单次数 > 5')
    page.locator('.el-dialog:visible button:has-text("创建规则")').click()
    page.wait_for_timeout(2500)
    b.close()
