# -*- coding: utf-8 -*-
"""阶段3：核心页面「设计稿 vs 实现」截图对比，供审美评估"""
from playwright.sync_api import sync_playwright
import json, urllib.request, os

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
DESIGN_DIR = r'D:\MOYUYOWPC\APPdocs\admin'
OUT_DIR = 'ui_compare_20260801'

# (路由, 设计稿文件)
PAIRS = [
    ('/dashboard', 'admin-dashboard.html'),
    ('/orders', 'admin-orders.html'),
    ('/products', 'admin-products.html'),
    ('/users', 'admin-users.html'),
    ('/marketing', 'admin-marketing.html'),
    ('/refund', 'admin-refund-manage.html'),
]

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def measure(page):
    """提取页面关键布局参数（taste评估数据）"""
    return page.evaluate('''() => {
      const cs = getComputedStyle(document.body);
      const cards = [...document.querySelectorAll('.el-card, .kpi-card, .panel, .bottom-panel, .data-card')];
      const btns = [...document.querySelectorAll('button, .el-button')];
      const h1 = document.querySelector('h1, h2, .page-title-area h1, .page-header h2');
      return {
        bodyFont: cs.fontSize,
        bodyColor: cs.color,
        primaryBtn: btns.length ? (getComputedStyle(btns.find(b => b.className && String(b.className).includes('primary')) || btns[0]).backgroundColor) : 'N/A',
        cardCount: cards.length,
        cardRadius: cards.length ? getComputedStyle(cards[0]).borderRadius : 'N/A',
        cardShadow: cards.length ? getComputedStyle(cards[0]).boxShadow : 'N/A',
        cardBg: cards.length ? getComputedStyle(cards[0]).backgroundColor : 'N/A',
        titleFont: h1 ? getComputedStyle(h1).fontSize : 'N/A',
        pageWidth: document.documentElement.scrollWidth,
        maxWidth: cs.maxWidth,
        padding: cs.padding,
        bg: cs.backgroundColor
      };
    }''')

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    token = login()
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        # 实现页面截图 + 测量
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        report = []
        for route, design in PAIRS:
            entry = {'route': route, 'design': design}
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(2200)
                impl_file = os.path.join(OUT_DIR, f'impl_{route.strip("/").replace("/", "_")}.png')
                page.screenshot(path=impl_file, full_page=False)
                entry['impl_metrics'] = measure(page)
                print(f'实现 {route} 已截图')
            except Exception as e:
                print(f'实现 {route} 失败: {str(e)[:80]}')
            # 设计稿截图 + 测量（file:// 打开）
            try:
                design_path = os.path.join(DESIGN_DIR, design)
                page.goto(f'file:///{design_path.replace(chr(92), "/")}', timeout=20000, wait_until='load')
                page.wait_for_timeout(1200)
                design_file = os.path.join(OUT_DIR, f'design_{design.replace(".html", "")}.png')
                page.screenshot(path=design_file, full_page=False)
                entry['design_metrics'] = measure(page)
                print(f'设计稿 {design} 已截图')
            except Exception as e:
                print(f'设计稿 {design} 失败: {str(e)[:80]}')
            report.append(entry)
        browser.close()
    with open('ui_compare_metrics.json', 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(f'\n截图目录: {OUT_DIR}')

if __name__ == '__main__':
    main()
