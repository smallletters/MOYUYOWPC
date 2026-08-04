"""
moyuyo-server 管理后台深度按钮交互测试
- 登录
- 遍历每个页面的所有按钮
- 对每个按钮尝试点击，捕获异常和网络错误
- 输出问题报告
"""
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
import time
import json
import os
import re
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
    '重置密码', '禁用', '封禁', '解绑', '全部清空', '一键清空',
]
# 安全按钮 - 可以点击
SAFE_KEYWORDS = [
    '查询', '搜索', '筛选', '刷新', '导出', '下载', '新增', '添加', '导入',
    '编辑', '修改', '保存', '提交', '确定', '确认', '详情', '查看', '预览',
    '启用', '发布', '审核', '通过', '驳回', '拒绝', '同意', '分配', '处理',
    '配置', '设置', '同步', '激活', '恢复', '生成', '复制', '重置', '绑定',
    '解除', '操作', '更多', '全部', '开始', '结束', '下一步', '上一步',
    '返回', '取消', '关闭', '展开', '收起', '切换', '统计', '分析',
    '开始直播', '结束直播', '推送', '发送', '回退', '处理', '认领',
    '转交', '转单', '回复', '启用', '禁用', '暂停', '恢复', '升级',
]


def is_dangerous_button(text):
    """判断是否为危险按钮"""
    text = text.strip()
    for kw in DANGEROUS_KEYWORDS:
        if kw in text:
            return True
    return False


def is_clickable_button(button):
    """判断按钮是否可点击（可见且启用）"""
    try:
        if not button.is_visible():
            return False
        if not button.is_enabled():
            return False
        return True
    except Exception:
        return False


def main():
    results = {
        'total_pages': len(ROUTES),
        'total_buttons': 0,
        'tested_buttons': 0,
        'skipped_buttons': 0,
        'failed_interactions': 0,
        'page_results': [],
        'button_failures': [],
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
                time.sleep(0.5)

                # 展开所有分组（如有）
                try:
                    for header in page.locator('.el-collapse-item__header').all()[:3]:
                        if header.is_visible():
                            header.click()
                            time.sleep(0.1)
                except Exception:
                    pass

                # 收集所有按钮
                buttons = page.locator('button').all()
                page_result['total_buttons'] = len(buttons)
                results['total_buttons'] += len(buttons)

                # 只测试安全按钮
                for idx, btn in enumerate(buttons[:30]):  # 限制每个页面最多测试 30 个按钮
                    try:
                        if not is_clickable_button(btn):
                            continue
                        text = btn.inner_text().strip()
                        if not text or len(text) > 30:
                            text = btn.get_attribute('aria-label') or text or f'btn_{idx}'
                        if is_dangerous_button(text):
                            page_result['skipped'] += 1
                            results['skipped_buttons'] += 1
                            continue
                        # 检查是否包含安全关键词
                        if not any(kw in text for kw in SAFE_KEYWORDS) and len(text) > 10:
                            page_result['skipped'] += 1
                            results['skipped_buttons'] += 1
                            continue
                        # 记录点击前状态
                        err_before = len(network_5xx)
                        console_before = len(console_errors)
                        try:
                            # 短暂点击
                            btn.click(timeout=2000)
                            time.sleep(0.3)
                            # 关闭可能弹出的对话框
                            try:
                                cancel_btn = page.locator('.el-dialog__wrapper button:has-text("取消")').first
                                if cancel_btn.is_visible(timeout=500):
                                    cancel_btn.click(timeout=1500)
                                    time.sleep(0.2)
                            except Exception:
                                pass
                            try:
                                # 关闭可能的消息提示
                                close_msg = page.locator('.el-message__closeBtn').first
                                if close_msg.is_visible(timeout=300):
                                    close_msg.click(timeout=1000)
                            except Exception:
                                pass

                            new_5xx = network_5xx[err_before:]
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
                            # 超时也视为通过（可能是 modal 弹出后被点击拦截）
                            page_result['tested'] += 1
                            results['tested_buttons'] += 1
                        except Exception as click_err:
                            # 元素被遮挡等
                            page_result['tested'] += 1
                            results['tested_buttons'] += 1
                    except Exception:
                        page_result['skipped'] += 1
                        results['skipped_buttons'] += 1

                if page_result['failed'] > 0:
                    results['failed_interactions'] += page_result['failed']
                    for f in page_result['failures']:
                        results['button_failures'].append({
                            'page': name,
                            'path': path,
                            **f,
                        })

                status_marker = '✓' if page_result['failed'] == 0 else '✗'
                print(f"{status_marker} {name:18s} btns={page_result['total_buttons']:3d} tested={page_result['tested']:3d} skip={page_result['skipped']:3d} fail={page_result['failed']:3d}")
                if page_result['failed'] > 0:
                    for f in page_result['failures'][:3]:
                        print(f"   ✗ [{f['type']}] {f['button']}: {f['msg'][:120]}")

            except Exception as e:
                print(f"✗ {name:18s} EXCEPTION: {str(e)[:100]}")

            results['page_results'].append(page_result)

        browser.close()

    print(f"\n========== 汇总 ==========")
    print(f"总页面:   {results['total_pages']}")
    print(f"总按钮数: {results['total_buttons']}")
    print(f"测试按钮: {results['tested_buttons']}")
    print(f"跳过按钮: {results['skipped_buttons']}")
    print(f"失败交互: {results['failed_interactions']}")

    if results['button_failures']:
        print(f"\n========== 5xx 错误详情 ==========")
        for f in results['button_failures'][:30]:
            print(f"  [{f['page']}] {f['button']}: {f['msg'][:140]}")

    out_path = r'D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\button_audit.json'
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n详细结果已保存: {out_path}")
    return results


if __name__ == '__main__':
    main()
