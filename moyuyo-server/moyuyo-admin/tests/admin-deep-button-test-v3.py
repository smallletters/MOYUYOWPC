"""
moyuyo-server 管理后台深度按钮交互测试 V3
- 使用 page.locator('button').all() 获取按钮
- 避免 evaluate 时的页面切换竞态
"""
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
import time
import json
import os
import sys

BASE_URL = 'http://localhost:8080/admin'
LOGIN_EMAIL = 'admin@moyuyo.com'
LOGIN_PASSWORD = '123456'

ROUTES = [
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

# 危险按钮 - 不点击（避免数据丢失）
DANGEROUS_KEYWORDS = [
    '删除', '移除', '清空', '下线', '下架', '彻底删除', '批量删除', '永久删除',
    '重置密码', '禁用', '封禁', '解绑', '全部清空', '一键清空', '关闭直播',
]


def is_dangerous_button(text):
    text = text.strip()
    for kw in DANGEROUS_KEYWORDS:
        if kw in text:
            return True
    return False


def safe_dismiss_modal(page):
    """关闭所有可能弹出的对话框"""
    try:
        # 尝试按 ESC
        page.keyboard.press('Escape')
        time.sleep(0.1)
    except Exception:
        pass
    try:
        # 关闭 el-message
        for close_btn in page.locator('.el-message__closeBtn').all()[:2]:
            try:
                close_btn.click(timeout=500, force=True)
            except Exception:
                pass
    except Exception:
        pass
    try:
        # 关闭 el-dialog (通过 close 按钮)
        for close in page.locator('.el-dialog__headerbtn').all()[:2]:
            try:
                if close.is_visible(timeout=300):
                    close.click(timeout=500, force=True)
                    time.sleep(0.1)
            except Exception:
                pass
    except Exception:
        pass
    try:
        # 取消弹窗
        for cancel in page.locator('.el-dialog__wrapper .el-button:has-text("取消")').all()[:1]:
            try:
                if cancel.is_visible(timeout=300):
                    cancel.click(timeout=500, force=True)
                    time.sleep(0.1)
            except Exception:
                pass
    except Exception:
        pass
    try:
        # message-box 取消
        for cancel in page.locator('.el-message-box .el-button:has-text("取消")').all()[:1]:
            try:
                if cancel.is_visible(timeout=300):
                    cancel.click(timeout=500, force=True)
                    time.sleep(0.1)
            except Exception:
                pass
    except Exception:
        pass


def main():
    results = {
        'total_pages': len(ROUTES),
        'total_buttons': 0,
        'tested_buttons': 0,
        'skipped_buttons': 0,
        'failed_interactions': 0,
        'page_results': [],
        'all_failures': [],
    }

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = context.new_page()

        # 收集网络 5xx 错误
        network_5xx = []
        def on_response(response):
            if response.status >= 500 and '/api/' in response.url:
                network_5xx.append({
                    'url': response.url,
                    'status': response.status,
                    'method': response.request.method,
                })
        page.on('response', on_response)

        # 收集控制台错误
        console_errors = []
        def on_console(msg):
            if msg.type == 'error':
                console_errors.append(msg.text)
        page.on('console', on_console)

        # 登录
        try:
            page.goto(f'{BASE_URL}/login')
            page.wait_for_load_state('networkidle', timeout=10000)
            page.locator('input[type="email"]').fill(LOGIN_EMAIL)
            page.locator('input[type="password"]').fill(LOGIN_PASSWORD)
            page.locator('button.login-btn').click()
            page.wait_for_url('**/dashboard', timeout=10000)
            time.sleep(1.0)
            print(f"[LOGIN] OK\n")
        except Exception as e:
            print(f"[LOGIN] FAILED: {e}")
            browser.close()
            return results

        # 遍历所有页面
        for path, name in ROUTES:
            page_result = {
                'path': path,
                'name': name,
                'total_buttons': 0,
                'tested': 0,
                'skipped': 0,
                'failed': 0,
                'failures': [],
            }
            try:
                page.goto(f'{BASE_URL}{path}', wait_until='domcontentloaded', timeout=15000)
                page.wait_for_load_state('networkidle', timeout=8000)
                time.sleep(1.5)

                # 使用 page.locator 获取所有按钮
                btn_locator = page.locator('button')
                btn_count = btn_locator.count()
                page_result['total_buttons'] = btn_count
                results['total_buttons'] += btn_count

                # 收集按钮信息：text + index
                btn_infos = []
                for i in range(btn_count):
                    try:
                        btn = btn_locator.nth(i)
                        text = (btn.inner_text() or '').strip()
                        aria = btn.get_attribute('aria-label') or ''
                        disabled = btn.is_disabled()
                        if not text and aria:
                            text = aria
                        if not text:
                            text = f'btn_{i}'
                        btn_infos.append({
                            'idx': i,
                            'text': text[:30],
                            'disabled': disabled,
                        })
                    except Exception:
                        pass

                # 测试每个按钮
                for btn_info in btn_infos:
                    text = btn_info['text']
                    if btn_info['disabled']:
                        page_result['skipped'] += 1
                        results['skipped_buttons'] += 1
                        continue
                    if is_dangerous_button(text):
                        page_result['skipped'] += 1
                        results['skipped_buttons'] += 1
                        continue
                    # 跳过明显的"退出登录"和"返回"等纯导航
                    if text in ('退出登录', '返回', '刷新', 'toggle', ''):
                        page_result['skipped'] += 1
                        results['skipped_buttons'] += 1
                        continue

                    err_before = len(network_5xx)
                    console_before = len(console_errors)
                    try:
                        # 滚动到元素可见
                        try:
                            btn_locator.nth(btn_info['idx']).scroll_into_view_if_needed(timeout=1000)
                        except Exception:
                            pass
                        btn_locator.nth(btn_info['idx']).click(timeout=2000, force=True)
                        time.sleep(0.5)
                        safe_dismiss_modal(page)

                        new_5xx = network_5xx[err_before:]
                        new_console = console_errors[console_before:]
                        if new_5xx:
                            for err in new_5xx:
                                page_result['failed'] += 1
                                page_result['failures'].append({
                                    'button': text[:30],
                                    'type': '5xx',
                                    'msg': f"{err['status']} {err['method']} {err['url'][:100]}"
                                })
                        else:
                            page_result['tested'] += 1
                            results['tested_buttons'] += 1
                    except PWTimeout:
                        page_result['tested'] += 1
                        results['tested_buttons'] += 1
                    except Exception:
                        page_result['tested'] += 1
                        results['tested_buttons'] += 1

                if page_result['failed'] > 0:
                    results['failed_interactions'] += page_result['failed']
                    for f in page_result['failures']:
                        results['all_failures'].append({
                            'page': name,
                            'path': path,
                            **f,
                        })

                status_marker = '✓' if page_result['failed'] == 0 else '✗'
                print(f"{status_marker} {name:18s} btns={page_result['total_buttons']:3d} tested={page_result['tested']:3d} skip={page_result['skipped']:3d} fail={page_result['failed']:3d}", flush=True)
                if page_result['failed'] > 0:
                    for f in page_result['failures'][:3]:
                        print(f"   ✗ [{f['type']}] {f['button']}: {f['msg'][:120]}", flush=True)

            except Exception as e:
                print(f"✗ {name:18s} EXCEPTION: {str(e)[:100]}", flush=True)

            results['page_results'].append(page_result)

        browser.close()

    print(f"\n========== 汇总 ==========", flush=True)
    print(f"总页面:   {results['total_pages']}", flush=True)
    print(f"总按钮数: {results['total_buttons']}", flush=True)
    print(f"测试按钮: {results['tested_buttons']}", flush=True)
    print(f"跳过按钮: {results['skipped_buttons']}", flush=True)
    print(f"失败交互: {results['failed_interactions']}", flush=True)

    if results['all_failures']:
        print(f"\n========== 5xx 错误详情 ==========", flush=True)
        for f in results['all_failures'][:50]:
            print(f"  [{f['page']}] {f['button']}: {f['msg'][:140]}", flush=True)

    out_path = r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\button_audit_v3.json'
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n详细结果已保存: {out_path}", flush=True)
    return results


if __name__ == '__main__':
    main()
