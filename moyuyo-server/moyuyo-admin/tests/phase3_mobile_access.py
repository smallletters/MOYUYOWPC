# -*- coding: utf-8 -*-
"""阶段3：窄屏内容可达性验证 - 确认水平滚动后所有内容可访问"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 393, 'height': 852})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        for route, name in [('/orders', 'orders'), ('/cs-performance', 'cs-performance')]:
            p.goto(f'http://localhost:5173/admin{route}', timeout=20000, wait_until='domcontentloaded')
            p.wait_for_timeout(1500)
            # 检测关键内容是否在视口内（KPI、表格头、按钮）
            checks = p.evaluate('''() => {
              const doc = document.documentElement;
              // 侧边栏 + 内容区：确认 main 区域可访问
              const main = document.querySelector('.admin-main');
              const content = document.querySelector('.admin-content');
              const kpi = document.querySelector('.kpi-grid, .stats-grid, .kpi-row');
              const table = document.querySelector('.data-table, .el-table');
              return {
                scrollW: doc.scrollWidth,
                clientW: doc.clientWidth,
                mainLeft: main ? main.getBoundingClientRect().left : null,
                mainWidth: main ? main.getBoundingClientRect().width : null,
                kpiVisible: kpi ? kpi.getBoundingClientRect().width > 0 : false,
                tableVisible: table ? table.getBoundingClientRect().width > 0 : false
              };
            }''')
            print(f'{route}: {json.dumps(checks, ensure_ascii=False)}')
            # 截图窄屏
            p.screenshot(path=f'phase3_mobile_{name}.png')
        b.close()


if __name__ == '__main__':
    main()
