# -*- coding: utf-8 -*-
"""阶段4：页面跳转与数据传递验收（列表 -> 详情/编辑页）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

# 跳转链路：列表页 -> 点击操作 -> 目标页
FLOWS = [
    ('/orders', '订单详情', '/orders/'),
    ('/products', '编辑商品', '/products/edit/'),
    ('/complaint', '投诉处理详情', '/complaint-handle'),
    ('/push-manage', '推送详情', '/push-detail'),
    ('/settlement', '结算详情', '/settlement-detail'),
    ('/reviews', '内容审核详情', '/content-review-detail'),
]

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    console_errs = []
    page.on('console', lambda m: console_errs.append(m.text) if m.type == 'error' else None)
    for route, btn_text, expect_prefix in FLOWS:
        try:
            page.goto(BASE_FE + '/admin' + route, timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(1500)
            # 点击第一个表格行的操作按钮（编辑/详情/查看）
            link = page.locator(f'.el-table__body button:has-text("{btn_text}")').first
            if not link.count():
                print(f'{route}: 未找到「{btn_text}」按钮')
                continue
            link.click(timeout=3000)
            page.wait_for_timeout(1800)
            url = page.url
            ok = expect_prefix in url
            print(f'{route} -> {url} [{"OK" if ok else "FAIL"}]')
        except Exception as e:
            print(f'{route}: 跳转异常 {str(e)[:80]}')
    print('console errors:', console_errs if console_errs else '无')
    b.close()
print('done')
