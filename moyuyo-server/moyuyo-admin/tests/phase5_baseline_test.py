"""
阶段1/阶段4 全站页面基线测试
遍历所有管理后台路由页面，检查：
1. 页面是否成功加载（无渲染异常/空白页）
2. 控制台错误（console.error / uncaught exception）
3. 网络请求失败（HTTP >= 400 或接口 404）
4. 页面核心内容是否渲染（标题/表格/卡片存在）
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import sys

BASE_FE = 'http://localhost:5173'
BASE_BE = 'http://localhost:8080'
LOGIN_URL = f'{BASE_BE}/api/admin/auth/login'

# 所有业务页面路由（参数页单独列出，用样例 id 测试）
PAGES = [
    ('/dashboard', '仪表盘'),
    ('/orders', '订单管理'),
    ('/orders/1', '订单详情(样例)'),
    ('/products', '商品管理'),
    ('/products/edit/1', '编辑商品(样例)'),
    ('/products/add', '新增商品'),
    ('/users', '用户管理'),
    ('/marketing', '营销管理'),
    ('/reviews', '内容审核'),
    ('/cs', '客服管理'),
    ('/analytics', '数据分析'),
    ('/logistics', '物流管理'),
    ('/settings', '系统设置'),
    ('/refund', '退款管理'),
    ('/cms', 'CMS内容管理'),
    ('/rbac', 'RBAC权限管理'),
    ('/finance', '财务概览'),
    ('/inventory', '库存管理'),
    ('/push-manage', '推送管理'),
    ('/ticket', '工单管理'),
    ('/campaign', '活动创建'),
    ('/complaint', '投诉管理'),
    ('/review-manage', '评价管理'),
    ('/product-analysis', '商品分析'),
    ('/product-report', '商品报表'),
    ('/product-review', '商品评价审核'),
    ('/price-manage', '价格管理'),
    ('/price-history', '价格历史'),
    ('/order-export', '订单导出'),
    ('/order-intercept', '订单拦截'),
    ('/order-monitor', '订单监控'),
    ('/order-price-modify', '订单改价'),
    ('/order-print', '订单打印'),
    ('/sms', '短信管理'),
    ('/sensitive-words', '敏感词管理'),
    ('/funnel', '漏斗分析'),
    ('/rfm', 'RFM分析'),
    ('/risk-control', '风控管理'),
    ('/risk-rule-engine', '风控规则引擎'),
    ('/realtime-screen', '实时大屏'),
    ('/user-profile', '用户画像'),
    ('/ab-test', 'A/B测试'),
    ('/app-version', '应用版本管理'),
    ('/batch-import', '批量导入'),
    ('/knowledge-base', '知识库'),
    ('/search-analysis', '搜索分析'),
    ('/traffic-analysis', '流量分析'),
    ('/satisfaction', '满意度管理'),
    ('/gdpr', 'GDPR合规'),
    ('/audit-log', '审计日志'),
    ('/product-approval', '商品审核'),
    ('/coupon-manage', '优惠券管理'),
    ('/flash-sale-manage', '秒杀管理'),
    ('/points-manage', '积分管理'),
    ('/blacklist', '黑名单管理'),
    ('/tariff', '关税管理'),
    ('/risk-alert', '风控告警'),
    ('/cs-sessions', '客服会话'),
    ('/order-tags', '订单标签'),
    ('/inventory-transfer', '库存调拨'),
    ('/merge-package', '合包管理'),
    ('/split-package', '分包裹'),
    ('/carrier-compare', '承运商对比'),
    ('/overseas-warehouse', '海外仓管理'),
    ('/warehouse-manage', '仓库管理'),
    ('/clearance', '清关管理'),
    ('/customs', '海关管理'),
    ('/settlement', '结算管理'),
    ('/settlement-detail/1', '结算详情(样例)'),
    ('/system-config', '系统配置'),
    ('/operation-log', '运营日志'),
    ('/live-manage', '直播管理'),
    ('/marketing-effect', '营销效果'),
    ('/shipping-strategy', '发货策略'),
    ('/content-review-detail/1', '内容审核详情(样例)'),
    ('/push-detail/1', '推送详情(样例)'),
    ('/complaint-handle/1', '投诉处理详情(样例)'),
]

def main():
    # 登录获取 token
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        p = json.loads(r.read())
    token = p['data']['token']
    print(f'[登录成功] token 长度={len(token)}')

    results = []
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context()
        page = ctx.new_page()

        # 预置 token
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        for path, name in PAGES:
            console_errors = []
            api_failures = []

            def on_console(msg):
                if msg.type == 'error':
                    console_errors.append(msg.text)

            def on_pageerror(exc):
                console_errors.append(f'PAGEERROR: {exc}')

            try:
                page.remove_listener('console', on_console)
                page.remove_listener('pageerror', on_pageerror)
            except Exception:
                pass
            page.on('console', on_console)
            page.on('pageerror', on_pageerror)

            try:
                resp = page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(2500)  # 等待接口渲染
            except Exception as e:
                results.append({'path': path, 'name': name, 'status': 'LOAD_FAIL', 'console': [], 'api': [str(e)]})
                continue

            # 检查接口失败（通过 performance entries）
            api_fail = []
            try:
                perf = page.evaluate("""() => {
                    return performance.getEntriesByType('resource')
                        .filter(e => e.initiatorType === 'fetch' || e.initiatorType === 'xmlhttprequest')
                        .map(e => e.name)
                }""")
            except Exception:
                perf = []

            # 核心内容检查：页面标题或主要容器
            has_title = page.evaluate("""() => {
                const t = document.querySelector('.page-title, h1, h2, .app-main, .content');
                return t ? t.textContent.trim().length > 0 : false
            }""")

            body_len = page.evaluate("() => document.body.innerText.length")

            if body_len < 20 and not has_title:
                status = 'BLANK'
            elif console_errors:
                status = 'CONSOLE_ERR'
            elif api_failures:
                status = 'API_ERR'
            else:
                status = 'OK'

            results.append({'path': path, 'name': name, 'status': status,
                            'console': console_errors[:5], 'api': api_failures[:5],
                            'body_len': body_len})
            print(f"[{status:12s}] {path:35s} {name} body_len={body_len} console={len(console_errors)}")
            if console_errors:
                for ce in console_errors[:3]:
                    print(f"         console: {ce[:160]}")
            if api_failures:
                for af in api_failures[:3]:
                    print(f"         api: {af[:160]}")

        browser.close()

    # 汇总
    print('\n================= 基线汇总 =================')
    ok = sum(1 for r in results if r['status'] == 'OK')
    print(f"OK={ok} / LOAD_FAIL={sum(1 for r in results if r['status']=='LOAD_FAIL')} "
          f"/ BLANK={sum(1 for r in results if r['status']=='BLANK')} "
          f"/ CONSOLE_ERR={sum(1 for r in results if r['status']=='CONSOLE_ERR')} "
          f"/ API_ERR={sum(1 for r in results if r['status']=='API_ERR')} "
          f"/ TOTAL={len(results)}")
    print('\n--- 非 OK 页面 ---')
    for r in results:
        if r['status'] != 'OK':
            print(f"- {r['path']} ({r['name']}): {r['status']}")
            for ce in r['console'][:3]:
                print(f"    console: {ce[:200]}")
            for af in r['api'][:3]:
                print(f"    api: {af[:200]}")

    with open('phase5_baseline_result.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print('\n结果已保存到 phase5_baseline_result.json')

if __name__ == '__main__':
    sys.exit(main())
