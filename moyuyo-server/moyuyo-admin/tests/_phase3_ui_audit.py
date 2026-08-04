# -*- coding: utf-8 -*-
"""阶段3：UI 一致性 + 布局溢出 + 弹窗交互验证（阶段2修改页面 + 核心页面）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

# 待验证页面：阶段2修改页 + 核心页
ROUTES = [
    '/tariff', '/risk-rule-engine', '/settlement', '/customs',
    '/ab-test', '/risk-alert', '/order-tags', '/batch-import',
    '/carrier-compare', '/blacklist', '/sensitive-words', '/push-manage',
    '/cms', '/product-report', '/order-print', '/inventory-transfer',
    '/dashboard', '/orders', '/products', '/users', '/marketing', '/refund',
]

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def audit(page):
    """DOM 级审计：溢出、硬编码颜色、弹窗、控制台错误"""
    return page.evaluate('''() => {
      const docW = document.documentElement.scrollWidth;
      const winW = window.innerWidth;
      // 硬编码颜色统计（排除 design tokens 变量本身）
      const hardColor = [...document.querySelectorAll('*')].filter(el => {
        const s = getComputedStyle(el);
        return /rgba?\(/.test(s.color) || /rgba?\(/.test(s.backgroundColor);
      }).length;
      const dialogs = document.querySelectorAll('.el-dialog').length;
      return { overflow: docW > winW + 2, docW, winW, hardColorEls: hardColor, dialogCount: dialogs };
    }''')

def main():
    token = login()
    report = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        console_errors = []
        page.on('console', lambda msg: console_errors.append(msg.text) if msg.type == 'error' else None)
        for route in ROUTES:
            entry = {'route': route, 'status': 'OK', 'issues': []}
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1800)
                m = audit(page)
                if m['overflow']:
                    entry['status'] = 'OVERFLOW'
                    entry['issues'].append(f'横向溢出: docW={m["docW"]} winW={m["winW"]}')
                entry['overflow'] = m['overflow']
                entry['hardColorEls'] = m['hardColorEls']
                # 尝试打开第一个主按钮（新建/新增类）
                try:
                    btn = page.locator('button.el-button--primary, .header-actions button, button:has-text("新建"), button:has-text("新增")').first
                    if btn.count():
                        btn.click(timeout=3000)
                        page.wait_for_timeout(600)
                        dlg = page.locator('.el-dialog:visible, .el-overlay:visible').count()
                        entry['dialogOpened'] = dlg > 0
                        # 关闭弹窗
                        page.keyboard.press('Escape')
                        page.wait_for_timeout(400)
                    else:
                        entry['dialogOpened'] = False
                except Exception as e:
                    entry['dialogOpened'] = 'ERR'
                    entry['issues'].append(f'弹窗打开异常: {str(e)[:60]}')
                print(f'{route} -> {entry["status"]} dlg={entry["dialogOpened"]} hardColor={m["hardColorEls"]}')
            except Exception as e:
                entry['status'] = 'FAIL'
                entry['issues'].append(str(e)[:100])
                print(f'{route} -> FAIL {str(e)[:60]}')
            entry['consoleErrors'] = list(console_errors)
            console_errors.clear()
            report.append(entry)
        browser.close()
    with open('ui_audit_20260801.json', 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print('\n完成，报告: ui_audit_20260801.json')

if __name__ == '__main__':
    main()
