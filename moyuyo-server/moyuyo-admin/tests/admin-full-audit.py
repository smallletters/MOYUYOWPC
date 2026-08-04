"""
moyuyo-server 管理后台全量功能测试
- 登录
- 遍历所有路由
- 收集控制台错误、网络 4xx/5xx
- 输出问题报告
"""
from playwright.sync_api import sync_playwright
import time
import json
import os
import sys

BASE_URL = 'http://localhost:8080/admin'
LOGIN_EMAIL = 'admin@moyuyo.com'
LOGIN_PASSWORD = '123456'

# 从 router/index.js 和 AdminLayout.vue 提取的完整路由清单
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


def main():
    results = {
        'total_pages': len(ROUTES),
        'passed': 0,
        'failed': 0,
        'errors': [],
        'page_details': []
    }

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        context = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = context.new_page()

        # 收集网络错误
        network_errors = []

        def on_response(response):
            if response.status >= 400 and '/api/' in response.url:
                network_errors.append({
                    'url': response.url,
                    'status': response.status,
                    'method': response.request.method
                })

        page.on('response', on_response)

        # 收集控制台错误
        console_errors = []

        def on_console(msg):
            if msg.type == 'error':
                console_errors.append(msg.text)

        page.on('console', on_console)

        # 收集页面异常
        page_errors = []

        def on_pageerror(err):
            page_errors.append(str(err))

        page.on('pageerror', on_pageerror)

        # 登录
        try:
            page.goto(f'{BASE_URL}/login')
            page.wait_for_load_state('networkidle')
            page.locator('input[type="email"]').fill(LOGIN_EMAIL)
            page.locator('input[type="password"]').fill(LOGIN_PASSWORD)
            page.locator('button.login-btn').click()
            page.wait_for_url('**/dashboard', timeout=10000)
            print(f"[LOGIN] OK: {page.url}")
        except Exception as e:
            print(f"[LOGIN] FAILED: {e}")
            results['errors'].append({'page': '/login', 'error': str(e)})
            browser.close()
            return results

        # 展开所有导航分组
        try:
            for header in page.locator('.nav-group-header').all():
                try:
                    header.click()
                    time.sleep(0.1)
                except Exception:
                    pass
        except Exception as e:
            print(f"[NAV EXPAND] warning: {e}")

        # 遍历所有页面
        for path, name in ROUTES:
            page_errors_before = len(page_errors)
            console_errors_before = len(console_errors)
            net_errors_before = len(network_errors)

            try:
                page.goto(f'{BASE_URL}{path}', wait_until='domcontentloaded', timeout=15000)
                page.wait_for_load_state('networkidle', timeout=10000)
                time.sleep(0.5)

                # 统计按钮数量
                buttons = page.locator('button').all()
                button_count = len(buttons)

                # 检查页面是否有错误提示
                has_error = False
                error_text = ''
                error_selectors = ['.el-message--error', '.error-msg', '[class*="error"]']
                for sel in error_selectors:
                    err_elements = page.locator(sel).all()
                    for el in err_elements[:3]:
                        try:
                            text = el.inner_text().strip()
                            if text and '错误' in text or '失败' in text or 'Error' in text:
                                has_error = True
                                error_text += text + '; '
                        except Exception:
                            pass

                # 收集本次页面的问题
                page_issues = []
                for err in page_errors[page_errors_before:]:
                    page_issues.append({'type': 'page_error', 'msg': err})
                for err in console_errors[console_errors_before:]:
                    page_issues.append({'type': 'console_error', 'msg': err})
                for err in network_errors[net_errors_before:]:
                    page_issues.append({'type': 'network', 'msg': f"{err['status']} {err['method']} {err['url']}"})

                status = 'PASS'
                # 有 500 错误或严重 page error 算失败
                critical = any(
                    i['type'] in ('page_error',) or
                    (i['type'] == 'network' and i['msg'].startswith('5'))
                    for i in page_issues
                )
                if critical:
                    status = 'FAIL'
                    results['failed'] += 1
                else:
                    results['passed'] += 1

                results['page_details'].append({
                    'path': path,
                    'name': name,
                    'status': status,
                    'buttons': button_count,
                    'issues': page_issues,
                    'error_text': error_text
                })

                # 简单日志
                issue_count = len(page_issues)
                marker = '✓' if status == 'PASS' else '✗'
                print(f"{marker} {name:20s} btn={button_count:3d} issues={issue_count}")
                if page_issues:
                    for i in page_issues[:3]:
                        print(f"   - {i['type']}: {i['msg'][:120]}")

            except Exception as e:
                results['failed'] += 1
                results['errors'].append({'page': path, 'error': str(e)})
                results['page_details'].append({
                    'path': path, 'name': name, 'status': 'FAIL',
                    'error': str(e)[:200]
                })
                print(f"✗ {name:20s} EXCEPTION: {str(e)[:120]}")

        browser.close()

    print(f"\n========== 汇总 ==========")
    print(f"总页面: {results['total_pages']}")
    print(f"通过:   {results['passed']}")
    print(f"失败:   {results['failed']}")

    # 汇总所有 network 5xx 错误
    all_5xx = [i for d in results['page_details']
               for i in d.get('issues', []) if i.get('type') == 'network' and i.get('msg', '').startswith('5')]
    if all_5xx:
        print(f"\n========== 5xx 错误汇总 ({len(all_5xx)}) ==========")
        for e in all_5xx[:20]:
            print(f"  {e.get('msg', '')[:160]}")

    # 汇总所有 page_error
    all_page_err = [i for d in results['page_details']
                    for i in d.get('issues', []) if i.get('type') == 'page_error']
    if all_page_err:
        print(f"\n========== 页面JS异常 ({len(all_page_err)}) ==========")
        for e in all_page_err[:20]:
            print(f"  {e.get('msg', '')[:160]}")

    # 输出到文件
    out_path = r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\full_audit.json'
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n详细结果已保存: {out_path}")

    return results


if __name__ == '__main__':
    main()
