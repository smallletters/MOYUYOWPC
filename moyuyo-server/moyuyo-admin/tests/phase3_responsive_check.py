# -*- coding: utf-8 -*-
"""阶段3：响应式适配检查 - 检查各核心页面在 393px 与 768px 宽度下的表现"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
ROUTES = [
    ('/dashboard', 'dashboard'),
    ('/orders', 'orders'),
    ('/products', 'products'),
    ('/users', 'users'),
    ('/marketing', 'marketing'),
    ('/refund', 'refund'),
    ('/cs-performance', 'cs-performance'),
    ('/settlement', 'settlement'),
]


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        for width, label in [(393, 'mobile'), (768, 'tablet'), (1440, 'desktop')]:
            ctx = b.new_context(viewport={'width': width, 'height': 852})
            p = ctx.new_page()
            p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
            print(f'===== {label} ({width}px) =====')
            for route, name in ROUTES:
                try:
                    p.goto(f'http://localhost:5173/admin{route}', timeout=20000, wait_until='domcontentloaded')
                    p.wait_for_timeout(1500)
                    metrics = p.evaluate('''() => {
                      const doc = document.documentElement;
                      return {
                        scrollW: doc.scrollWidth,
                        clientW: doc.clientWidth,
                        overflowX: doc.scrollWidth > doc.clientWidth + 2,
                        sidebarVisible: !!document.querySelector('.admin-sidebar') && getComputedStyle(document.querySelector('.admin-sidebar')).display !== 'none',
                        mainPadding: getComputedStyle(document.querySelector('.admin-content') || document.body).paddingLeft
                      };
                    }''')
                    flag = 'OVF' if metrics['overflowX'] else 'OK '
                    print(f'{flag} {route:16s} scrollW={metrics["scrollW"]} clientW={metrics["clientW"]}')
                except Exception as e:
                    print(f'ERR {route}: {str(e)[:60]}')
            ctx.close()
        b.close()


if __name__ == '__main__':
    main()
