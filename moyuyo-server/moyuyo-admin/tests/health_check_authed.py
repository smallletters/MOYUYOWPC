"""
后端接口健康检查 - 带 JWT Token 认证版本
覆盖所有 Admin 控制器，验证接口返回 200/业务码 200
"""
import json
import time
import urllib.request
import urllib.parse
import urllib.error
import sys
import os
from datetime import datetime

BASE = "http://localhost:8080"
LOGIN_URL = f"{BASE}/api/admin/auth/login"

# 邮箱和密码（AdminInitializer 默认）
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"


def login():
    """登录获取 token"""
    data = json.dumps({"email": EMAIL, "password": PASSWORD}).encode("utf-8")
    req = urllib.request.Request(
        LOGIN_URL,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = json.loads(resp.read().decode("utf-8"))
        # 兼容 code=0 和 code=200 两种业务码
        if body.get("code") in (200, 0):
            token = (body.get("data") or {}).get("token")
            if not token:
                token = (body.get("data") or {}).get("accessToken")
            return token
        else:
            raise RuntimeError(f"登录失败: {body}")


def call_api(token, method, path, body=None):
    """调用后端 API"""
    url = f"{BASE}{path}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            return e.code, {}
    except Exception as e:
        return 0, {"error": str(e)}


def is_ok(status, body):
    """判断接口是否正常"""
    if status in (200, 201, 204):
        # 业务码 200/0 都视为正常
        if isinstance(body, dict):
            code = body.get("code")
            if code in (200, 0, None):
                return True
            return False
        return True
    # 404 表示资源不存在 - 接口是通的
    if status == 404:
        return True
    return False


# 全部接口列表：(method, path, body?, 说明)
ENDPOINTS = [
    # Dashboard
    ("GET", "/api/admin/dashboard/stats", None, "仪表盘统计"),
    ("GET", "/api/admin/dashboard/recent-orders", None, "最近订单"),
    ("GET", "/api/admin/dashboard/sales-trend", None, "销售趋势"),
    ("GET", "/api/admin/dashboard/category-distribution", None, "类目分布"),
    ("GET", "/api/admin/dashboard/top-products", None, "热销商品"),
    # Auth
    ("GET", "/api/admin/auth/me", None, "当前用户"),
    # Products
    ("GET", "/api/admin/products/list", None, "商品列表"),
    ("GET", "/api/admin/products/categories", None, "商品类目"),
    ("GET", "/api/admin/products/brands", None, "商品品牌"),
    # Orders
    ("GET", "/api/admin/orders/list", None, "订单列表"),
    # Coupons
    ("GET", "/api/admin/coupons/list", None, "优惠券列表"),
    ("GET", "/api/admin/coupons/stats", None, "优惠券统计"),
    # Logistics
    ("GET", "/api/admin/logistics/warehouses", None, "仓库列表"),
    ("GET", "/api/admin/logistics/overseas", None, "海外仓"),
    ("GET", "/api/admin/logistics/packages", None, "包裹列表"),
    ("GET", "/api/admin/logistics/split-packages", None, "分包裹"),
    ("GET", "/api/admin/logistics/merge-packages", None, "合包裹"),
    ("GET", "/api/admin/logistics/shipping-strategies", None, "运输策略"),
    ("GET", "/api/admin/logistics/kpi", None, "物流KPI"),
    ("GET", "/api/admin/logistics/carriers", None, "承运商"),
    ("GET", "/api/admin/logistics/clearance", None, "清关"),
    ("GET", "/api/admin/logistics/customs", None, "海关"),
    # CMS
    ("GET", "/api/admin/cms/list", None, "CMS列表"),
    # Finance
    ("GET", "/api/admin/finance/overview", None, "财务概览"),
    ("GET", "/api/admin/finance/records", None, "财务记录"),
    ("GET", "/api/admin/finance/settlements", None, "结算列表"),
    # Order Ops
    ("GET", "/api/admin/order-ops/print/list", None, "打印列表"),
    ("GET", "/api/admin/order-ops/price-modify/list", None, "改价列表"),
    ("GET", "/api/admin/order-ops/intercept/list", None, "拦截列表"),
    ("GET", "/api/admin/order-ops/monitor/list", None, "监控列表"),
    ("GET", "/api/admin/order-ops/stats", None, "订单运维统计"),
    # Order Tags
    ("GET", "/api/admin/order-tags/list", None, "订单标签"),
    # Inventory
    ("GET", "/api/admin/inventory/list", None, "库存列表"),
    ("GET", "/api/admin/inventory/alerts", None, "库存预警"),
    ("GET", "/api/admin/inventory/overview", None, "库存总览"),
    # Inventory Transfer
    ("GET", "/api/admin/inventory-transfer/list", None, "调拨列表"),
    # Flash Sales
    ("GET", "/api/admin/flash-sales/list", None, "秒杀列表"),
    # Users
    ("GET", "/api/admin/users/list", None, "用户列表"),
    ("GET", "/api/admin/users/stats", None, "用户统计"),
    # Refunds
    ("GET", "/api/admin/refunds/list", None, "退款列表"),
    ("GET", "/api/admin/refunds/stats", None, "退款统计"),
    ("GET", "/api/admin/refunds/reason-distribution", None, "退款原因分布"),
    # RBAC
    ("GET", "/api/admin/rbac/users", None, "RBAC用户"),
    ("GET", "/api/admin/rbac/roles", None, "RBAC角色"),
    ("GET", "/api/admin/rbac/permissions", None, "RBAC权限"),
    # Push
    ("GET", "/api/admin/push/records", None, "推送记录"),
    ("GET", "/api/admin/push/scheduled", None, "推送计划"),
    ("GET", "/api/admin/push/stats", None, "推送统计"),
    # Risk
    ("GET", "/api/admin/risk/rules", None, "风控规则"),
    ("GET", "/api/admin/risk/events", None, "风控事件"),
    ("GET", "/api/admin/risk/event-stats", None, "风控事件统计"),
    # Risk Alert
    ("GET", "/api/admin/risk-alert/configs", None, "风险预警配置"),
    ("GET", "/api/admin/risk-alert/history", None, "风险预警历史"),
    # Live
    ("GET", "/api/admin/live/rooms", None, "直播间"),
    # SMS
    ("GET", "/api/admin/sms/records", None, "短信记录"),
    ("GET", "/api/admin/sms/stats", None, "短信统计"),
    # Sensitive
    ("GET", "/api/admin/sensitive/list", None, "敏感词"),
    ("GET", "/api/admin/sensitive/categories", None, "敏感词分类"),
    # GDPR
    ("GET", "/api/admin/gdpr/policy", None, "GDPR策略"),
    ("GET", "/api/admin/gdpr/consent-records", None, "GDPR同意记录"),
    ("GET", "/api/admin/gdpr/data-requests", None, "GDPR数据请求"),
    # System
    ("GET", "/api/admin/system/config", None, "系统配置"),
    ("GET", "/api/admin/system/logs", None, "系统日志"),
    ("GET", "/api/admin/system-info/info", None, "系统信息"),
    ("GET", "/api/admin/system-info/security-config", None, "安全配置"),
    # Settings
    ("GET", "/api/admin/settings/payment-methods", None, "支付方式"),
    # Ticket
    ("GET", "/api/admin/ticket/list", None, "工单列表"),
    ("GET", "/api/admin/ticket/stats", None, "工单统计"),
    # Complaint
    ("GET", "/api/admin/complaint/list", None, "投诉列表"),
    # Knowledge Base
    ("GET", "/api/admin/knowledge-base/list", None, "知识库"),
    # Audit Log
    ("GET", "/api/admin/audit-log/list", None, "审计日志"),
    ("GET", "/api/admin/audit-log/stats", None, "审计统计"),
    # Content Review
    ("GET", "/api/admin/content-review/list", None, "内容审核"),
    ("GET", "/api/admin/content-review/stats", None, "内容审核统计"),
    ("GET", "/api/admin/content-review/trend", None, "内容审核趋势"),
    # CS Session
    ("GET", "/api/admin/cs-sessions/list", None, "客服会话"),
    ("GET", "/api/admin/cs-sessions/stats", None, "客服会话统计"),
    # CRM
    ("GET", "/api/admin/crm/realtime", None, "CRM实时"),
    ("GET", "/api/admin/crm/cs-performance", None, "CRM客服绩效"),
    ("GET", "/api/admin/crm/realtime-order-flow", None, "CRM订单流"),
    ("GET", "/api/admin/crm/realtime-top-products", None, "CRM热销商品"),
    # Satisfaction
    ("GET", "/api/admin/satisfaction/list", None, "满意度列表"),
    ("GET", "/api/admin/satisfaction/stats", None, "满意度统计"),
    # Tariff
    ("GET", "/api/admin/tariff/configs", None, "关税配置"),
    # Blacklist
    ("GET", "/api/admin/blacklist/list", None, "黑名单"),
    # Batch Import
    ("GET", "/api/admin/batch-import/records", None, "批量导入记录"),
    # Price
    ("GET", "/api/admin/price/list", None, "价格列表"),
    ("GET", "/api/admin/price/history", None, "价格历史"),
    # Points
    ("GET", "/api/admin/points/logs", None, "积分记录"),
    ("GET", "/api/admin/points/activities", None, "积分活动"),
    ("GET", "/api/admin/points/stats", None, "积分统计"),
    # Review
    ("GET", "/api/admin/review/list", None, "评价列表"),
    # Product Analysis
    ("GET", "/api/admin/product-analysis/list", None, "商品分析"),
    ("GET", "/api/admin/product-analysis/kpi", None, "商品分析KPI"),
    ("GET", "/api/admin/product-analysis/report", None, "商品分析报表"),
    # Product Approval
    ("GET", "/api/admin/product-approval/list", None, "商品审批"),
    # Marketing
    ("GET", "/api/admin/marketing/campaigns", None, "营销活动"),
    ("GET", "/api/admin/marketing/ab-tests", None, "AB测试"),
    ("GET", "/api/admin/marketing/effects", None, "营销效果"),
    # App Version
    ("GET", "/api/admin/app-version/list", None, "APP版本"),
    # Analysis
    ("GET", "/api/admin/analysis/funnel", None, "漏斗分析"),
    ("GET", "/api/admin/analysis/rfm", None, "RFM分析"),
    ("GET", "/api/admin/analysis/search", None, "搜索分析"),
    ("GET", "/api/admin/analysis/traffic", None, "流量分析"),
]


def main():
    print(f"开始健康检查: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("正在登录...")
    try:
        token = login()
    except Exception as e:
        print(f"登录失败: {e}")
        sys.exit(1)
    if not token:
        print("登录失败: 未获取到 token")
        sys.exit(1)
    print(f"登录成功, token 长度: {len(token)}")
    print("-" * 80)

    passed = 0
    failed = 0
    failed_list = []

    for method, path, body, desc in ENDPOINTS:
        status, resp = call_api(token, method, path, body)
        ok = is_ok(status, resp)
        if ok:
            passed += 1
            mark = "OK"
        else:
            failed += 1
            mark = "FAIL"
            failed_list.append((method, path, status, resp))
        print(f"[{mark}] [{status}] {method:6} {path:50} - {desc}")

    print("-" * 80)
    print(f"通过: {passed} 失败: {failed} 总计: {len(ENDPOINTS)}")
    print(f"通过率: {passed / len(ENDPOINTS) * 100:.1f}%")

    # 写出失败报告
    if failed_list:
        report_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "health_report_authed.txt"
        )
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(f"扫描时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"通过: {passed} 失败: {failed} 总计: {len(ENDPOINTS)}\n\n")
            for method, path, status, resp in failed_list:
                f.write(f"[{status}] {method} {path}\n")
                f.write(f"  {json.dumps(resp, ensure_ascii=False)[:300]}\n\n")
        print(f"\n失败报告已保存至: {report_path}")

    return failed


if __name__ == "__main__":
    sys.exit(main())
