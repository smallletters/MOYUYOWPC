# -*- coding: utf-8 -*-
"""阶段4：浏览器级写操作验收（tariff / risk-rule-engine 表单提交全链路）"""
from playwright.sync_api import sync_playwright
import json, urllib.request, time

BASE_FE = 'http://localhost:5173'
data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

def fill_and_submit_tariff(page):
    """新建关税配置并验证"""
    page.goto(BASE_FE + '/admin/tariff', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    page.locator('button:has-text("新建税率")').first.click(timeout=3000)
    page.wait_for_timeout(800)
    # 填写表单（按 label 定位）
    form = page.locator('.el-dialog:visible')
    form.locator('input[placeholder*="如：电子产品"]').fill('验收测试品类')
    form.locator('input[placeholder*="JP"]').fill('CN')
    # 税率/阈值用 input-number 的 input
    page.locator('.el-dialog:visible .el-input-number input').nth(0).fill('8')
    page.locator('.el-dialog:visible .el-input-number input').nth(1).fill('0')
    page.locator('.el-dialog:visible .el-input-number input').nth(2).fill('2000')
    form.locator('input[placeholder*="USD"]').fill('CNY')
    page.locator('.el-dialog:visible button:has-text("保存")').click()
    page.wait_for_timeout(1500)
    msg = page.locator('.el-message--success').all_text_contents()
    body = page.locator('.page-wrapper').inner_text()
    ok = any('成功' in m for m in msg) and '验收测试品类' in body
    print(f'tariff 新建: {"OK" if ok else "FAIL"} 提示={msg} 表格含新行={"验收测试品类" in body}')
    # 删除新行
    row = page.locator('tr:has-text("验收测试品类")').first
    row.locator('button:has-text("删除")').click()
    page.wait_for_timeout(600)
    page.locator('.el-message-box button:has-text("确定")').click() if page.locator('.el-message-box:visible').count() else None
    page.wait_for_timeout(1000)
    print('tariff 删除后残留:', '验收测试品类' in page.locator('.page-wrapper').inner_text())
    return ok

def fill_and_submit_rule(page):
    """新建风控规则并验证"""
    page.goto(BASE_FE + '/admin/risk-rule-engine', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    page.locator('button:has-text("新建规则")').first.click(timeout=3000)
    page.wait_for_timeout(800)
    form = page.locator('.el-dialog:visible')
    form.locator('input[placeholder*="输入规则名称"]').fill('验收规则A')
    form.locator('textarea').fill('单日下单次数 > 5')
    page.locator('.el-dialog:visible button:has-text("创建规则")').click()
    page.wait_for_timeout(1500)
    msg = page.locator('.el-message--success').all_text_contents()
    body = page.locator('.page-wrapper').inner_text()
    ok = any('成功' in m for m in msg) and '验收规则A' in body
    print(f'risk-rule 新建: {"OK" if ok else "FAIL"} 提示={msg} 表格含新行={"验收规则A" in body}')
    # 删除新行
    row = page.locator('tr:has-text("验收规则A")').first
    if row.count():
        row.locator('button:has-text("删除")').click()
        page.wait_for_timeout(600)
        if page.locator('.el-message-box:visible').count():
            page.locator('.el-message-box button:has-text("确定")').click()
        page.wait_for_timeout(1000)
    print('risk-rule 删除后残留:', '验收规则A' in page.locator('.page-wrapper').inner_text())
    return ok

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    errs = []
    page.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
    page.on('pageerror', lambda e: errs.append('PAGEERROR: ' + str(e)))
    r1 = fill_and_submit_tariff(page)
    r2 = fill_and_submit_rule(page)
    print('控制台错误:', errs if errs else '无')
    b.close()
print('验收汇总:', '全部通过' if r1 and r2 and not errs else '存在失败项')
