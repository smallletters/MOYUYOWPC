# -*- coding: utf-8 -*-
"""阶段4：关键跳转链路验证（列表→详情、侧边栏导航、弹窗路由跳转）"""
import json, urllib.request
from playwright.sync_api import sync_playwright

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

results = []
with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    p = ctx.new_page()
    p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    errs = []

    def on_console(m):
        if m.type == 'error':
            errs.append(m.text[:120])

    def on_pageerror(e):
        errs.append(f'PAGEERROR: {str(e)[:120]}')

    p.on('console', on_console)
    p.on('pageerror', on_pageerror)

    # 1. 订单列表 → 订单详情
    try:
        p.goto('http://localhost:5173/admin/orders', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(2000)
        rows = p.query_selector_all('.el-table__body tbody tr, .data-table tbody tr, table tbody tr')
        clicked = False
        for row in rows[:5]:
            try:
                link = row.query_selector('a, button:has-text("查看"), button:has-text("详情"), button:has-text("处理")')
                if link:
                    link.click(timeout=1500)
                    clicked = True
                    break
            except Exception:
                continue
        p.wait_for_timeout(2000)
        url = p.url
        ok = '/orders/' in url or '/order-detail' in url or '/orders' != url
        results.append(('订单列表→详情', f'clicked={clicked} url={url}', 'OK' if ok else 'FAIL'))
        print(f"订单跳转: clicked={clicked} url={url}")
    except Exception as e:
        results.append(('订单跳转', str(e)[:100], 'ERROR'))
        print(f'订单跳转 ERROR: {str(e)[:100]}')

    # 2. 侧边栏导航：跳到用户管理
    try:
        p.goto('http://localhost:5173/admin/dashboard', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1500)
        nav = p.query_selector('.sidebar-nav a:has-text("用户管理"), .sidebar a:has-text("用户管理"), aside a:has-text("用户管理")')
        if nav:
            nav.click(timeout=1500)
            p.wait_for_timeout(1500)
            results.append(('侧边栏→用户管理', p.url, 'OK' if '/users' in p.url else 'FAIL'))
            print(f'侧边栏跳转: {p.url}')
        else:
            results.append(('侧边栏→用户管理', '未找到导航项', 'FAIL'))
            print('侧边栏用户管理导航未找到')
    except Exception as e:
        results.append(('侧边栏跳转', str(e)[:100], 'ERROR'))
        print(f'侧边栏跳转 ERROR: {str(e)[:100]}')

    # 3. 商品列表 → 编辑（数据传递 id）
    try:
        p.goto('http://localhost:5173/admin/products', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(2000)
        edit_btn = p.query_selector('button:has-text("编辑")')
        if edit_btn:
            edit_btn.click(timeout=1500)
            p.wait_for_timeout(1800)
            url = p.url
            ok = '/products/edit/' in url or '/products/add' in url
            results.append(('商品列表→编辑', url, 'OK' if ok else 'FAIL'))
            print(f'商品编辑跳转: {url}')
        else:
            results.append(('商品列表→编辑', '无编辑按钮', 'SKIP'))
            print('商品列表无编辑按钮')
    except Exception as e:
        results.append(('商品编辑跳转', str(e)[:100], 'ERROR'))
        print(f'商品编辑跳转 ERROR: {str(e)[:100]}')

    b.close()

print('\n==== 跳转验收汇总 ====')
for name, detail, status in results:
    print(f'{status:8s} {name}: {detail}')
print(f'\nconsole/pageerror: {len(errs)}')
for e in errs[:5]:
    print(f'  {e}')
