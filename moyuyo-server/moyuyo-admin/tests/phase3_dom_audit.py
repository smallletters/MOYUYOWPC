# -*- coding: utf-8 -*-
"""阶段3：采集核心页面 DOM 视觉指标，对照设计稿 token 评估"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
ROUTES = [
    ('/dashboard', 'admin-dashboard.html'),
    ('/orders', 'admin-orders.html'),
    ('/products', 'admin-products.html'),
    ('/users', 'admin-users.html'),
    ('/marketing', 'admin-marketing.html'),
    ('/refund', 'admin-refund-manage.html'),
]

# 设计稿 token 基准
DESIGN_TOKENS = {
    'primary': '#007aff',
    'bg': '#ffffff',
    'text800': '#1d1d1f',
    'radius': '12px',
    'titleFont': '22px',
    'cardBg': '#ffffff',
}


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    report = []
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        for route, design in ROUTES:
            try:
                p.goto(f'http://localhost:5173/admin{route}', timeout=20000, wait_until='domcontentloaded')
                p.wait_for_timeout(1800)
                metrics = p.evaluate('''() => {
                  const cs = getComputedStyle(document.body);
                  const h1 = document.querySelector('h1');
                  const h2 = document.querySelector('h2');
                  const cards = document.querySelectorAll('.kpi-card, .card, .el-card, [class*=card]');
                  const btns = document.querySelectorAll('.btn-primary, .el-button--primary, button.btn');
                  const card = cards[0] ? getComputedStyle(cards[0]) : null;
                  const btn = btns[0] ? getComputedStyle(btns[0]) : null;
                  const overflowX = document.documentElement.scrollWidth > document.documentElement.clientWidth + 2;
                  return {
                    bodyFont: cs.fontSize,
                    bodyColor: cs.color,
                    titleFont: (h2 || h1) ? getComputedStyle(h2 || h1).fontSize : null,
                    titleWeight: (h2 || h1) ? getComputedStyle(h2 || h1).fontWeight : null,
                    cardRadius: card ? card.borderRadius : null,
                    cardBg: card ? card.backgroundColor : null,
                    cardShadow: card ? card.boxShadow : null,
                    btnBg: btn ? btn.backgroundColor : null,
                    btnRadius: btn ? btn.borderRadius : null,
                    cardCount: cards.length,
                    btnCount: btns.length,
                    overflow: overflowX
                  };
                }''')
                report.append({'route': route, 'design': design, 'impl': metrics})
                print(f'{route} title={metrics["titleFont"]} cardRadius={metrics["cardRadius"]} btnBg={metrics["btnBg"]} overflow={metrics["overflow"]}')
            except Exception as e:
                print(f'ERR {route}: {str(e)[:80]}')
        b.close()
    with open('phase3_dom_metrics.json', 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
