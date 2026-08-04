# -*- coding: utf-8 -*-
"""调试 risk-rule-engine 409 来源"""
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
    bad = []
    page.on('response', lambda r: bad.append((r.status, r.url)) if r.status >= 400 else None)
    page.on('console', lambda m: print('CONSOLE:', m.text) if m.type == 'error' else None)
    page.goto(BASE_FE + '/admin/risk-rule-engine', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    print('新建规则按钮:', page.locator('button:has-text("新建规则")').count())
    page.locator('button:has-text("新建规则")').first.click(timeout=3000)
    page.wait_for_timeout(800)
    print('弹窗可见:', page.locator('.el-dialog:visible').count())
    form = page.locator('.el-dialog:visible')
    print('名称输入框:', form.locator('input[placeholder*="输入规则名称"]').count())
    form.locator('input[placeholder*="输入规则名称"]').fill('验收规则B' + str(__import__('time').time())[-4:])
    form.locator('textarea').fill('单日下单次数 > 5')
    page.locator('.el-dialog:visible button:has-text("创建规则")').click()
    page.wait_for_timeout(2000)
    msgs = page.locator('.el-message').all_text_contents()
    print('消息:', msgs)
    print('>=400 响应:', bad)
    b.close()
