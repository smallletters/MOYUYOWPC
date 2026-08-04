# -*- coding: utf-8 -*-
"""阶段4 全站集成验收：所有路由渲染 + console 错误 + 接口失败 + 溢出检测（2026-08-01）
使用 5174 dev server（新实例，deps 预构建完整）
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5174'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

ROUTES = [
    '/login', '/dashboard', '/orders', '/products', '/products/add', '/users', '/marketing',
    '/reviews', '/cs', '/analytics', '/logistics', '/settings', '/refund', '/cms', '/rbac',
    '/finance', '/inventory', '/push-manage', '/ticket', '/campaign', '/complaint',
    '/review-manage', '/product-analysis', '/product-report', '/product-review',
    '/price-manage', '/price-history', '/order-export', '/order-intercept', '/order-monitor',
    '/order-price-modify', '/order-print', '/sms', '/sensitive-words', '/funnel', '/rfm',
    '/risk-control', '/risk-rule-engine', '/realtime-screen', '/user-profile', '/ab-test',
    '/app-version', '/batch-import', '/knowledge-base', '/search-analysis',
    '/traffic-analysis', '/satisfaction', '/gdpr', '/audit-log', '/product-approval',
    '/coupon-manage', '/flash-sale-manage', '/points-manage', '/blacklist', '/tariff',
    '/risk-alert', '/cs-sessions', '/order-tags', '/inventory-transfer', '/merge-package',
    '/split-package', '/carrier-compare', '/overseas-warehouse', '/warehouse-manage',
    '/clearance', '/customs', '/settlement', '/settlement-detail', '/system-config',
    '/operation-log', '/live-manage', '/marketing-effect', '/shipping-strategy',
    '/content-review-detail', '/push-detail', '/complaint-handle',
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
    all_http = []     # (route, status, url)
    current_route = {'v': ''}

    def on_console(msg):
        if msg.type == 'error':
            all_console.append((current_route['v'], msg.text))

    def on_pageerror(e):
        all_console.append((current_route['v'], f'PAGEERROR: {e}'))

    def on_response(r):
        if r.status >= 400 and '/api/' in r.url:
            all_http.append((current_route['v'], r.status, r.url))

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
            try:
                page.goto(f'{BASE_FE}/admin{route}', wait_until='networkidle', timeout=15000)
                page.wait_for_timeout(600)
                overflow = page.evaluate(
                    "() => document.documentElement.scrollWidth > document.documentElement.clientWidth")
                results.append({'route': route, 'status': 'OK', 'overflow': overflow})
                print(f'OK  {route}  overflow={overflow}')
            except Exception as e:
                results.append({'route': route, 'status': 'ERR', 'error': str(e)})
                print(f'ERR {route}  {e}')

        # 汇总
        console_err_routes = {}
        for route, text in all_console:
            console_err_routes.setdefault(route, []).append(text)
        http_fail = [(r, s, u) for r, s, u in all_http]

        report = {
            'total': len(results),
            'ok': sum(1 for r in results if r['status'] == 'OK'),
            'err': [r for r in results if r['status'] != 'OK'],
            'overflow': [r['route'] for r in results if r.get('overflow')],
            'console_errors_by_route': console_err_routes,
            'total_console_errors': len(all_console),
            'http_4xx_5xx': http_fail,
        }
        with open('tests/acceptance_20260801.json', 'w', encoding='utf-8') as f:
            json.dump(report, f, ensure_ascii=False, indent=2)

        print(f'\n=== 验收汇总 ===')
        print(f'总页面: {len(results)}，OK: {report["ok"]}，ERR: {len(report["err"])}')
        print(f'横向溢出页面: {report["overflow"]}')
        print(f'console 错误: {len(all_console)} 条，涉及页面: {list(console_err_routes.keys())}')
        print(f'接口 4xx/5xx: {len(http_fail)} 条')
        for r, s, u in http_fail[:20]:
            print(f'   [{s}] {r} -> {u}')
        browser.close()


if __name__ == '__main__':
    main()
