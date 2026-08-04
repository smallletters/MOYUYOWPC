"""阶段4 跳转链路集成验证：页面间跳转 + 数据传递"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        token = json.loads(r.read())['data']['token']

    checks = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        errs = []
        page.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
        page.on('pageerror', lambda e: errs.append(f'PAGEERROR: {e}'))

        # 1. 登录页跳转仪表盘
        page.goto(f'{BASE_FE}/admin/login', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(1500)
        # 已登录应自动跳 dashboard
        checks.append(('登录→仪表盘', '/dashboard' in page.url))

        # 2. 仪表盘 → 订单管理（侧边栏）
        page.goto(f'{BASE_FE}/admin/dashboard', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2000)
        try:
            page.locator('.sidebar-nav').get_by_text('订单管理').first.click()
            page.wait_for_timeout(2000)
            checks.append(('仪表盘→订单列表', '/orders' in page.url))
        except Exception as e:
            checks.append(('仪表盘→订单列表', f'FAIL {str(e)[:60]}'))

        # 3. 订单列表 → 订单详情（动态获取真实 id）
        try:
            page.locator('tbody tr button').filter(has_text='详情').first.click(timeout=5000)
            page.wait_for_timeout(2000)
            # 动态断言：URL 进入 /orders/ 且携带数字 id
            import re
            m = re.search(r'/orders/(\d+)', page.url)
            checks.append(('订单列表→详情', bool(m)))
        except Exception as e:
            checks.append(('订单列表→详情', f'FAIL {str(e)[:60]}'))

        # 4. 详情返回 → 商品管理 → 编辑商品
        page.goto(f'{BASE_FE}/admin/products', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2000)
        try:
            page.locator('tbody tr button').first.click(timeout=5000)
            page.wait_for_timeout(2000)
            checks.append(('商品列表→编辑', '/products/edit/' in page.url))
        except Exception as e:
            checks.append(('商品列表→编辑', f'FAIL {str(e)[:60]}'))

        # 5. 财务 → 结算详情（query 传参）
        page.goto(f'{BASE_FE}/admin/finance', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        try:
            page.get_by_text('查看全部', exact=False).first.click(timeout=4000)
            page.wait_for_timeout(1500)
            checks.append(('财务→结算', '/settlement' in page.url))
        except Exception as e:
            checks.append(('财务→结算', f'FAIL {str(e)[:60]}'))

        # 6. 客服 → 工单（query 传参）
        page.goto(f'{BASE_FE}/admin/cs', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        try:
            page.locator('button, .action-btn, a').filter(has_text='工单').first.click(timeout=4000)
            page.wait_for_timeout(1500)
            checks.append(('客服→工单', '/ticket' in page.url))
        except Exception as e:
            checks.append(('客服→工单', f'FAIL {str(e)[:60]}'))

        # 7. 营销 → 活动创建
        page.goto(f'{BASE_FE}/admin/marketing', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2000)
        try:
            page.get_by_text('创建活动', exact=True).first.click(timeout=4000)
            page.wait_for_timeout(1500)
            checks.append(('营销→活动创建', '/campaign' in page.url))
        except Exception as e:
            checks.append(('营销→活动创建', f'FAIL {str(e)[:60]}'))

        # 8. 侧边栏导航遍历（随机 6 个）
        sidebar_ok = 0
        for nav in ['用户管理', '退款管理', '内容审核', '数据分析', '物流管理', '财务概览']:
            page.goto(f'{BASE_FE}/admin/dashboard', timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(1500)
            try:
                page.locator('.sidebar-nav').get_by_text(nav).first.click(timeout=4000)
                page.wait_for_timeout(1500)
                body_len = page.evaluate("() => document.body.innerText.length")
                if body_len > 50:
                    sidebar_ok += 1
            except Exception:
                pass
        checks.append((f'侧边栏导航({6}个主菜单)', f'{sidebar_ok}/6 正常'))

        print('=== 跳转链路集成验证 ===')
        all_pass = True
        for name, result in checks:
            ok = isinstance(result, bool) and result or (isinstance(result, str) and 'FAIL' not in result and result.endswith('/6 正常') and result.startswith('6/6'))
            if isinstance(result, str) and '正常' in result and '/' in result:
                ok = result.split('/')[0] == result.split('/')[1].split(' ')[0]
            print(f'[{"PASS" if ok else "FAIL"}] {name}: {result}')
            if not ok:
                all_pass = False

        print('console 错误数:', len(errs))
        for e in errs[:5]:
            print('  ', e[:150])
        print('整体:', '全部通过' if all_pass and not errs else '存在异常，见上')
        browser.close()

if __name__ == '__main__':
    main()
