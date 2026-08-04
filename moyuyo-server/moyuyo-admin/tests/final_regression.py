# -*- coding: utf-8 -*-
"""最终回归：sensitive-words 搜索（验证 toLowerCase 修复）+ 关键页面无 console 错误"""
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
    errs = []
    p.on('console', lambda m: errs.append(f'console: {m.text[:100]}') if m.type == 'error' else None)
    p.on('pageerror', lambda e: errs.append(f'PAGEERROR: {str(e)[:100]}'))

    # sensitive-words 搜索（触发 filterWords 修复路径）
    p.goto('http://localhost:5173/admin/sensitive-words', timeout=20000, wait_until='domcontentloaded')
    p.wait_for_timeout(1800)
    search = p.query_selector('input[placeholder*="搜索"]')
    if search:
        search.fill('违禁')
        p.wait_for_timeout(800)
        body = p.evaluate('() => document.body.innerText')
        print(f'sensitive 搜索"违禁": 列表渲染={len(body)}字, 结果含"违禁品"={"违禁品" in body}')
    # 验证列表有数据
    items = p.query_selector_all('.sw-word-item')
    print(f'sensitive 词条数: {len(items)}')

    # risk-alert 页面（修复 JSON 后列表正常）
    p.goto('http://localhost:5173/admin/risk-alert', timeout=20000, wait_until='domcontentloaded')
    p.wait_for_timeout(1800)
    rows = p.query_selector_all('.el-table__body tbody tr')
    print(f'risk-alert 配置行数: {len(rows)}')

    # shipping-strategy（修复字段后列表正常）
    p.goto('http://localhost:5173/admin/shipping-strategy', timeout=20000, wait_until='domcontentloaded')
    p.wait_for_timeout(1800)
    rows2 = p.query_selector_all('.el-table__body tbody tr')
    print(f'shipping-strategy 行数: {len(rows2)}')

    b.close()

print(f'\nconsole/pageerror 总数: {len(errs)}')
for e in errs[:5]:
    print(f'  {e}')
print('\n最终回归: ' + ('PASS' if not errs else 'FAIL'))
