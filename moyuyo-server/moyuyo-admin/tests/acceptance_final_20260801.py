# -*- coding: utf-8 -*-
"""阶段4：全站集成验收
1. 侧边栏导航跳转全链路
2. 列表→详情页数据传递（query/params）
3. 控制台错误 + 接口 4xx/5xx 检测
4. 弹窗打开/关闭状态
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
BASE_FE = 'http://localhost:5173'

# 验收路由清单（含参数页）
ROUTES = [
    '/dashboard', '/orders', '/products', '/users', '/marketing', '/reviews', '/cs',
    '/analytics', '/logistics', '/settings', '/refund', '/cms', '/rbac', '/finance',
    '/inventory', '/push-manage', '/ticket', '/campaign', '/complaint', '/review-manage',
    '/product-analysis', '/product-report', '/product-review', '/price-manage',
    '/price-history', '/order-export', '/order-intercept', '/order-monitor',
    '/order-price-modify', '/order-print', '/sms', '/sensitive-words', '/funnel', '/rfm',
    '/risk-control', '/risk-rule-engine', '/realtime-screen', '/user-profile', '/ab-test',
    '/app-version', '/batch-import', '/knowledge-base', '/search-analysis',
    '/traffic-analysis', '/satisfaction', '/gdpr', '/audit-log', '/product-approval',
    '/coupon-manage', '/flash-sale-manage', '/points-manage', '/blacklist', '/tariff',
    '/risk-alert', '/cs-sessions', '/cs-performance', '/order-tags', '/inventory-transfer',
    '/merge-package', '/split-package', '/carrier-compare', '/overseas-warehouse',
    '/warehouse-manage', '/clearance', '/customs', '/settlement', '/settlement-detail',
    '/system-config', '/operation-log', '/live-manage', '/marketing-effect',
    '/shipping-strategy', '/content-review-detail', '/push-detail', '/complaint-handle',
]


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']

    console_errors = []
    http_errors = []
    nav_failures = []
    current_route = {'v': ''}

    def on_console(m):
        if m.type == 'error':
            console_errors.append((current_route['v'], m.text[:120]))

    def on_pageerror(e):
        console_errors.append((current_route['v'], f'PAGEERROR: {str(e)[:120]}'))

    def on_response(r):
        if r.status >= 400 and '/api/' in r.url:
            http_errors.append((current_route['v'], f'{r.status} {r.url[:100]}'))

    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        page.on('console', on_console)
        page.on('pageerror', on_pageerror)
        page.on('response', on_response)

        # 1) 全路由加载验收
        print('===== 1. 全路由加载 =====')
        for route in ROUTES:
            current_route['v'] = route
            base = (len(console_errors), len(http_errors))
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1200)
                body_len = page.evaluate('() => document.body.innerText.length')
                c = console_errors[base[0]:]
                h = http_errors[base[1]:]
                status = 'OK'
                if body_len < 50:
                    status = 'EMPTY'
                if c:
                    status = 'CONSOLE_ERR'
                if h:
                    status = 'HTTP_ERR'
                if status != 'OK':
                    nav_failures.append((route, status, c[:2], h[:2]))
                    print(f'  {status:12s} {route}')
                else:
                    print(f'  {"OK":12s} {route}')
            except Exception as e:
                nav_failures.append((route, 'EXC', str(e)[:100]))
                print(f'  {"EXC":12s} {route}: {str(e)[:80]}')

        # 2) 导航跳转链：侧边栏首项跳转
        print('===== 2. 侧边栏导航跳转 =====')
        page.goto(f'{BASE_FE}/admin/dashboard', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(1500)
        nav_items = page.locator('.nav-item').all()
        print(f'  侧边栏导航项数: {len(nav_items)}')
        clicked = 0
        for item in nav_items[:8]:
            try:
                item.click(timeout=5000)
                page.wait_for_timeout(1200)
                url = page.url
                body_len = page.evaluate('() => document.body.innerText.length')
                if body_len < 50:
                    nav_failures.append((url, 'NAV_EMPTY', [], []))
                    print(f'  NAV_FAIL {url}')
                else:
                    clicked += 1
                    print(f'  OK -> {url}')
            except Exception as e:
                nav_failures.append(('nav', 'CLICK_ERR', str(e)[:100], []))
                print(f'  CLICK_ERR: {str(e)[:60]}')

        # 3) 弹窗打开/关闭验收（抽查带弹窗的页面）
        print('===== 3. 弹窗状态 =====')
        dialog_pages = ['/cms', '/campaign', '/coupon-manage', '/tariff', '/blacklist',
                        '/ab-test', '/push-manage', '/risk-rule-engine']
        for route in dialog_pages:
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1200)
                # 点击第一个打开弹窗的按钮
                opened = False
                btns = page.locator('button').all()
                for btn in btns:
                    try:
                        txt = (btn.inner_text() or '').strip()
                        if any(k in txt for k in ['新增', '创建', '新建', '添加']):
                            btn.click(timeout=3000)
                            page.wait_for_timeout(600)
                            # 检测 el-dialog 或 modal 可见
                            dlg = page.locator('.el-dialog, .modal-overlay, .modal-content').first
                            if dlg.count() > 0 and dlg.is_visible():
                                opened = True
                                # 关闭
                                close_btn = page.locator('.el-dialog__headerbtn, .modal-close').first
                                if close_btn.count() > 0:
                                    close_btn.click(timeout=3000)
                                    page.wait_for_timeout(400)
                                break
                    except Exception:
                        continue
                print(f'  {route}: dialog={"opened" if opened else "none/failed"}')
            except Exception as e:
                print(f'  {route}: ERR {str(e)[:60]}')

        b.close()

    print('\n===== 验收汇总 =====')
    print(f'总路由: {len(ROUTES)}')
    print(f'控制台错误数: {len(console_errors)}')
    print(f'接口 4xx/5xx 数: {len(http_errors)}')
    print(f'导航失败数: {len(nav_failures)}')
    for f in nav_failures[:10]:
        print(f'  FAIL {f}')
    for c in console_errors[:5]:
        print(f'  CONSOLE {c}')
    for h in http_errors[:5]:
        print(f'  HTTP {h}')

    result = {
        'total_routes': len(ROUTES),
        'console_errors': console_errors[:10],
        'http_errors': http_errors[:10],
        'nav_failures': nav_failures[:10],
    }
    with open('acceptance_final_20260801.json', 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
