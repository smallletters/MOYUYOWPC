# -*- coding: utf-8 -*-
"""全站路由冒烟测试：所有路由渲染 + console 错误 + 接口失败检测（2026-08-01）"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

ROUTES = [
    '/dashboard', '/orders', '/products', '/products/add', '/users', '/marketing', '/reviews',
    '/cs', '/analytics', '/logistics', '/settings', '/refund', '/cms', '/rbac', '/finance',
    '/inventory', '/push-manage', '/ticket', '/campaign', '/complaint', '/review-manage',
    '/product-analysis', '/product-report', '/product-review', '/price-manage', '/price-history',
    '/order-export', '/order-intercept', '/order-monitor', '/order-price-modify', '/order-print',
    '/sms', '/sensitive-words', '/funnel', '/rfm', '/risk-control', '/risk-rule-engine',
    '/realtime-screen', '/user-profile', '/ab-test', '/app-version', '/batch-import',
    '/knowledge-base', '/search-analysis', '/traffic-analysis', '/satisfaction', '/gdpr',
    '/audit-log', '/product-approval', '/coupon-manage', '/flash-sale-manage', '/points-manage',
    '/blacklist', '/tariff', '/risk-alert', '/cs-sessions', '/order-tags', '/inventory-transfer',
    '/merge-package', '/split-package', '/carrier-compare', '/overseas-warehouse',
    '/warehouse-manage', '/clearance', '/customs', '/settlement', '/settlement-detail',
    '/system-config', '/operation-log', '/live-manage', '/marketing-effect', '/shipping-strategy',
]


def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']


def main():
    token = login()
    results = []
    all_console = []  # (route, text)
    all_http = []     # (route, text)
    current_route = {'v': ''}

    def on_console(msg):
        if msg.type == 'error':
            all_console.append((current_route['v'], msg.text))

    def on_pageerror(e):
        all_console.append((current_route['v'], f'PAGEERROR: {e}'))

    def on_response(r):
        if r.status >= 400 and '/api/' in r.url:
            all_http.append((current_route['v'], f'{r.status} {r.url}'))

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        page.on('console', on_console)
        page.on('pageerror', on_pageerror)
        page.on('response', on_response)
        for route in ROUTES:
            current_route['v'] = route
            base_idx = (len(all_console), len(all_http))
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1800)
                body_len = page.evaluate('() => document.body.innerText.length')
                overflow = page.evaluate('() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2')
                c_err = [t for r, t in all_console[base_idx[0]:]]
                h_err = [t for r, t in all_http[base_idx[1]:]]
                status = 'OK'
                if body_len < 50:
                    status = 'EMPTY'
                if c_err:
                    status = 'CONSOLE_ERR'
                if h_err:
                    status = 'HTTP_ERR'
                results.append({'route': route, 'status': status, 'body': body_len,
                                'overflow': overflow, 'console': c_err[:3], 'http': h_err[:3]})
                print(f"{status:16s} {route}  body={body_len}  overflow={overflow}")
            except Exception as e:
                results.append({'route': route, 'status': 'ERROR', 'err': str(e)[:100]})
                print(f'ERROR {route}: {str(e)[:100]}')
        browser.close()

    ok = [r for r in results if r['status'] == 'OK']
    bad = [r for r in results if r['status'] != 'OK']
    print(f'\n==== 汇总: 通过 {len(ok)} / 异常 {len(bad)} / 总计 {len(results)} ====')
    for b in bad:
        print(json.dumps(b, ensure_ascii=False))
    with open('smoke_20260801.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
