"""逐页捕获 console 错误，定位触发 vite deps 报错的页面"""
import json
import urllib.request
from playwright.sync_api import sync_playwright

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request('http://localhost:8080/api/admin/auth/login', data=data, method='POST',
                             headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

PAGES = ['/order-export', '/content-review-detail/1', '/product-report', '/marketing-effect',
         '/product-analysis', '/user-profile', '/funnel', '/settlement', '/satisfaction',
         '/search-analysis', '/overseas-warehouse', '/risk-control', '/price-manage',
         '/system-config', '/sms', '/order-monitor', '/order-print', '/push-detail',
         '/complaint', '/clearance', '/customs', '/carrier-compare', '/inventory',
         '/split-package', '/warehouse-manage', '/realtime-screen', '/gdpr', '/rfm',
         '/review-manage', '/live-manage', '/complaint-handle', '/campaign']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    p = ctx.new_page()
    p.add_init_script("localStorage.setItem('admin_token', '%s')" % token)
    for route in PAGES:
        errs = []
        p.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
        p.on('pageerror', lambda e: errs.append('PAGEERROR: ' + str(e)))
        p.goto('http://localhost:5174/admin' + route, wait_until='networkidle', timeout=15000)
        p.wait_for_timeout(800)
        if errs:
            print('PAGE', route)
            for e in errs:
                print('   ', e[:200])
    b.close()
