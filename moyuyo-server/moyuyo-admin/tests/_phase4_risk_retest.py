# -*- coding: utf-8 -*-
"""重跑 risk-rule-engine 新建/删除浏览器链路验收"""
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
    errs = []
    page.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
    page.goto(BASE_FE + '/admin/risk-rule-engine', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    page.locator('button:has-text("新建规则")').first.click(timeout=3000)
    page.wait_for_timeout(800)
    form = page.locator('.el-dialog:visible')
    form.locator('input[placeholder*="输入规则名称"]').fill('验收规则D')
    form.locator('textarea').fill('单日下单次数 > 5 AND 设备指纹异常')
    page.locator('.el-dialog:visible button:has-text("创建规则")').click()
    page.wait_for_timeout(2000)
    msgs = page.locator('.el-message').all_text_contents()
    body = page.locator('.page-wrapper').inner_text()
    created = '验收规则D' in body
    print('消息:', msgs)
    print('表格含新行:', created)
    # 删除
    row = page.locator('tr:has-text("验收规则D")').first
    if row.count():
        row.locator('button:has-text("删除")').click()
        page.wait_for_timeout(600)
        if page.locator('.el-message-box:visible').count():
            page.locator('.el-message-box button:has-text("确定")').click()
        page.wait_for_timeout(1200)
    print('删除后残留:', '验收规则D' in page.locator('.page-wrapper').inner_text())
    print('控制台错误:', errs if errs else '无')
    b.close()
print('验收:', 'PASS' if created and not errs else 'FAIL')
