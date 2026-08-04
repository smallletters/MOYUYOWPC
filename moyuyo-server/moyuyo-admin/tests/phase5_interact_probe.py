"""
阶段2 交互探测器：批量验证各页面主操作按钮的响应
对每个页面：点击"新建/新增"等主操作按钮，检查页面响应方式：
- el-dialog/el-drawer 打开（visible）
- 自定义折叠表单展开（.open 类）
- 路由跳转
- Element 消息提示（开发中/成功/失败）
- 无响应（按钮失效 = BUG）
同时收集 console 错误与 HTTP 失败
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

# 页面 → 主操作按钮文本
PAGES = [
    ('/dashboard', '仪表盘', None),
    ('/orders', '订单管理', '搜索'),
    ('/products', '商品管理', None),
    ('/users', '用户管理', None),
    ('/marketing', '营销管理', '创建活动'),
    ('/reviews', '内容审核', None),
    ('/cs', '客服管理', None),
    ('/analytics', '数据分析', None),
    ('/logistics', '物流管理', None),
    ('/settings', '系统设置', None),
    ('/refund', '退款管理', None),
    ('/cms', 'CMS内容管理', '新建 Banner'),
    ('/rbac', 'RBAC权限管理', '新建角色'),
    ('/finance', '财务概览', None),
    ('/inventory', '库存管理', None),
    ('/push-manage', '推送管理', '新建推送'),
    ('/ticket', '工单管理', None),
    ('/campaign', '活动创建', '新建活动'),
    ('/complaint', '投诉管理', '新增投诉记录'),
    ('/review-manage', '评价管理', None),
    ('/product-analysis', '商品分析', None),
    ('/product-report', '商品报表', None),
    ('/product-review', '商品评价审核', None),
    ('/price-manage', '价格管理', None),
    ('/price-history', '价格历史', None),
    ('/order-export', '订单导出', '新建导出任务'),
    ('/order-intercept', '订单拦截', None),
    ('/order-monitor', '订单监控', None),
    ('/order-price-modify', '订单改价', None),
    ('/order-print', '订单打印', None),
    ('/sms', '短信管理', None),
    ('/sensitive-words', '敏感词管理', '新增敏感词'),
    ('/funnel', '漏斗分析', None),
    ('/rfm', 'RFM分析', None),
    ('/risk-control', '风控管理', None),
    ('/risk-rule-engine', '风控规则引擎', None),
    ('/realtime-screen', '实时大屏', None),
    ('/user-profile', '用户画像', None),
    ('/ab-test', 'A/B测试', '新建实验'),
    ('/app-version', '应用版本管理', None),
    ('/batch-import', '批量导入', None),
    ('/knowledge-base', '知识库', '新建文章'),
    ('/search-analysis', '搜索分析', None),
    ('/traffic-analysis', '流量分析', None),
    ('/satisfaction', '满意度管理', None),
    ('/gdpr', 'GDPR合规', '新建隐私政策'),
    ('/audit-log', '审计日志', None),
    ('/product-approval', '商品审核', None),
    ('/coupon-manage', '优惠券管理', '新建优惠券'),
    ('/flash-sale-manage', '秒杀管理', '新建秒杀'),
    ('/points-manage', '积分管理', None),
    ('/blacklist', '黑名单管理', '新建'),
    ('/tariff', '关税管理', None),
    ('/risk-alert', '风控告警', None),
    ('/cs-sessions', '客服会话', None),
    ('/order-tags', '订单标签', None),
    ('/inventory-transfer', '库存调拨', '新建调拨'),
    ('/merge-package', '合包管理', '新建'),
    ('/split-package', '分包裹', None),
    ('/carrier-compare', '承运商对比', '新建'),
    ('/overseas-warehouse', '海外仓管理', '新建'),
    ('/warehouse-manage', '仓库管理', '新建'),
    ('/clearance', '清关管理', '新建'),
    ('/customs', '海关管理', None),
    ('/settlement', '结算管理', None),
    ('/system-config', '系统配置', None),
    ('/operation-log', '运营日志', None),
    ('/live-manage', '直播管理', '新建直播'),
    ('/marketing-effect', '营销效果', '新建活动'),
    ('/shipping-strategy', '发货策略', None),
]

def get_token():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def main():
    token = get_token()
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        results = []

        for path, name, btn_text in PAGES:
            console_errors = []
            http_failures = []

            def on_console(msg):
                if msg.type == 'error':
                    console_errors.append(msg.text)
            def on_pageerror(exc):
                console_errors.append(f'PAGEERROR: {exc}')
            def on_response(resp):
                if resp.status >= 400 and '/api/' in resp.url:
                    http_failures.append(f'{resp.status} {resp.url}')
            try:
                page.remove_listener('console', on_console)
                page.remove_listener('pageerror', on_pageerror)
                page.remove_listener('response', on_response)
            except Exception:
                pass
            page.on('console', on_console)
            page.on('pageerror', on_pageerror)
            page.on('response', on_response)

            try:
                page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(2500)
            except Exception as e:
                print(f'[LOAD_FAIL] {path} ({name})')
                results.append({'path': path, 'status': 'LOAD_FAIL', 'detail': str(e)[:120]})
                continue

            issues = []

            # 点击主操作按钮
            if btn_text:
                clicked = False
                try:
                    btn = page.get_by_text(btn_text, exact=True).first
                    btn.click(timeout=4000)
                    clicked = True
                    page.wait_for_timeout(1200)
                except Exception:
                    # 尝试部分匹配
                    try:
                        btn = page.get_by_text(btn_text).first
                        btn.click(timeout=4000)
                        clicked = True
                        page.wait_for_timeout(1200)
                    except Exception as e:
                        issues.append(f'主按钮"{btn_text}"不可点击: {str(e)[:80]}')

                if clicked:
                    # 检测响应方式
                    dialog_visible = False
                    try:
                        dialog_visible = page.locator('.el-dialog, .el-drawer').first.is_visible()
                    except Exception:
                        pass
                    form_open = False
                    try:
                        form_open = page.locator('.sw-collapsible.open, .rbac-collapsible.open, .el-collapse-item.is-active, form:visible').count() > 0 or page.locator('.sw-collapsible, .rbac-collapsible').first.evaluate("el => el.className.includes('open')")
                    except Exception:
                        pass
                    url_changed = '/admin' + path != page.url.replace(f'{BASE_FE}/admin', '/admin')
                    msg = ''
                    try:
                        msg_el = page.locator('.el-message').first
                        if msg_el.is_visible():
                            msg = msg_el.inner_text()
                    except Exception:
                        pass

                    if dialog_visible:
                        resp_type = f'弹窗打开: {btn_text}'
                        # 关闭弹窗
                        try:
                            page.locator('.el-dialog__headerbtn, .el-overlay').first.click(timeout=2000)
                            page.wait_for_timeout(600)
                        except Exception:
                            page.keyboard.press('Escape')
                            page.wait_for_timeout(600)
                    elif url_changed:
                        resp_type = '路由跳转'
                        page.goto(f'{BASE_FE}/admin{path}', timeout=15000, wait_until='domcontentloaded')
                        page.wait_for_timeout(1500)
                    elif msg:
                        resp_type = f'消息提示: {msg[:50]}'
                    elif form_open:
                        resp_type = '折叠表单展开'
                        # 关闭折叠表单
                        try:
                            page.get_by_text('取消', exact=True).first.click(timeout=1500)
                        except Exception:
                            pass
                    elif btn_text in ('搜索', '重置', '筛选'):
                        # 搜索/重置/筛选类按钮：点击成功即视为正常（表格刷新不可直接观测）
                        resp_type = '列表刷新'
                    else:
                        resp_type = '无可见响应'
                        issues.append(f'点击"{btn_text}"后无任何响应')
            else:
                resp_type = '（无主操作按钮）'

            # 检查按钮/链接是否可点击（抽样验证页面交互性）
            clickable = 0
            try:
                clickable = page.locator('button:not([disabled]), .btn:not([disabled]), a').count()
            except Exception:
                pass

            # 汇总状态
            if issues:
                status = 'ISSUES'
            elif console_errors:
                status = 'CONSOLE_ERR'
                issues.append(f'console: {console_errors[0][:150]}')
            elif http_failures:
                status = 'HTTP_ERR'
                issues.append(f'http: {http_failures[0][:150]}')
            else:
                status = 'OK'

            print(f'[{status:11s}] {path:28s} {name:14s} 响应={resp_type[:45]} 可点元素={clickable}')
            for iss in issues[:3]:
                print(f'    - {iss}')

            results.append({'path': path, 'name': name, 'status': status,
                            'resp': resp_type, 'issues': issues[:3],
                            'console': console_errors[:2], 'http': http_failures[:2]})

        browser.close()

        print('\n================= 交互探测汇总 =================')
        from collections import Counter
        cnt = Counter(r['status'] for r in results)
        print(dict(cnt))
        print('\n--- 有问题的页面 ---')
        for r in results:
            if r['status'] != 'OK':
                print(f"- {r['path']} ({r['name']}): {r['status']}")
                for iss in r['issues'][:2]:
                    print(f"    {iss}")

        with open('phase5_interact_result.json', 'w', encoding='utf-8') as f:
            json.dump(results, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    main()
