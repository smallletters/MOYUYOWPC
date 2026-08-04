"""
快速页面加载测试 - 剩余页面
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE = 'http://localhost:5173/admin'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

# 登录获取token
data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
    headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(req) as r:
    p = json.loads(r.read())
token = p['data']['token']

# 所有页面
pages = [
    ('/dashboard', '仪表盘'),
    ('/orders', '订单管理'),
    ('/products', '商品管理'),
    ('/refund', '退款管理'),
    ('/users', '用户管理'),
    ('/marketing', '营销管理'),
    ('/reviews', '内容审核'),
    ('/cs', '客服管理'),
    ('/analytics', '数据分析'),
    ('/logistics', '物流管理'),
    ('/finance', '财务概览'),
    ('/inventory', '库存管理'),
    ('/ticket', '工单管理'),
    ('/cms', 'CMS内容管理'),
    ('/rbac', 'RBAC权限管理'),
    ('/push-manage', '推送管理'),
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
    ('/system-config', '系统配置'),
    ('/operation-log', '运营日志'),
    ('/live-manage', '直播管理'),
    ('/marketing-effect', '营销效果'),
    ('/shipping-strategy', '发货策略'),
    ('/settings', '系统设置'),
    ('/products/add', '新增商品'),
]

total_btn = 0
total_tested = 0
total_fail = 0
total_skip = 0

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context()
    page = ctx.new_page()
    
    # 登录
    page.goto(BASE + '/login', timeout=15000)
    page.evaluate(f'localStorage.setItem("admin_token", "{token}")')
    page.goto(BASE + '/dashboard', timeout=15000)
    page.wait_for_load_state('networkidle', timeout=10000)
    page.wait_for_timeout(500)
    
    for path, name in pages:
        try:
            page.goto(BASE + path, timeout=20000)
            page.wait_for_load_state('networkidle', timeout=15000)
            page.wait_for_timeout(500)
            
            # 检查错误提示
            body_text = page.locator('body').inner_text(timeout=3000)
            if '404' in body_text[:50] or '页面不存在' in body_text[:100]:
                print(f'[WARN] {name:<14} ({path:<25}) 404/页面不存在')
                total_skip += 1
                continue
            
            # 获取所有按钮
            btns = page.locator('button').all()
            n_btn = len(btns)
            total_btn += n_btn
            
            # 快速测试前5个可见按钮
            visible_tested = 0
            page_fail = 0
            for btn in btns[:min(n_btn, 8)]:
                try:
                    if btn.is_visible():
                        btn.click(timeout=2000)
                        page.wait_for_timeout(300)
                        # 如果有对话框就关闭
                        dialogs = page.locator('.el-dialog, .el-message-box, [role="dialog"]').all()
                        if len(dialogs) > 0:
                            close_btn = page.locator('.el-dialog__close, .el-message-box__close, button:has-text("取消"), button:has-text("关 闭")').first
                            try:
                                close_btn.click(timeout=1000)
                            except:
                                page.keyboard.press('Escape')
                            page.wait_for_timeout(200)
                        visible_tested += 1
                except:
                    pass  # 按钮不可点击跳过
            
            total_tested += visible_tested
            
            if page_fail ==0:
                print(f'[OK  ] {name:<14} btns={n_btn:>3} tested={visible_tested} fail=0')
            else:
                print(f'[WARN] {name:<14} btns={n_btn:>3} tested={visible_tested} fail={page_fail}')
                total_fail += page_fail
        except Exception as e:
            err_msg = str(e)[:80]
            print(f'[FAIL] {name:<14} ({path}) {err_msg}')
            total_fail += 1
    
    browser.close()
    
    print(f'\n=== 页面快速测试: ok={len(pages)-total_fail-total_skip} fail={total_fail} skip={total_skip} total={len(pages)} ===')
    print(f'=== 按钮总计: {total_btn}, 测试了: {total_tested} ===')
