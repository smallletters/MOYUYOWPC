#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段3 - 前端管理后台页面功能测试（按实际路由）"""
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeout

BASE = "http://127.0.0.1:5173"
OUTPUT = Path(r"D:\MOYUYOWPC\phase3_results.json")
SCREENSHOT_DIR = Path(r"D:\MOYUYOWPC\phase3_screenshots")
SCREENSHOT_DIR.mkdir(exist_ok=True)

# 按实际 router 路径（从 router/index.js 提取）
# 详情/编辑页先用占位 ID，登录后会自动替换为数据库中真实存在的第一条记录的 ID
PAGES = [
    ("Dashboard", "dashboard", "仪表盘"),
    ("OrderList", "orders", "订单管理"),
    ("OrderDetail", "orders/__ID__", "订单详情", "fetch_first_id", "/api/admin/orders/list", "id"),
    ("ProductList", "products", "商品管理"),
    ("ProductEdit", "products/edit/__ID__", "编辑商品", "fetch_first_id", "/api/admin/products/list", "id"),
    ("UserList", "users", "用户管理"),
    ("MarketingList", "marketing", "营销管理"),
    ("ContentReview", "reviews", "内容审核"),
    ("CustomerService", "cs", "客服管理"),
    ("Analytics", "analytics", "数据分析"),
    ("LogisticsList", "logistics", "物流管理"),
    ("SystemSettings", "settings", "系统设置"),
    ("RefundManage", "refund", "退款管理"),
    ("CmsManage", "cms", "CMS内容管理"),
    ("RbacManage", "rbac", "RBAC权限管理"),
    ("FinanceManage", "finance", "财务概览"),
    ("InventoryManage", "inventory", "库存管理"),
    ("PushManage", "push-manage", "推送管理"),
    ("TicketManage", "ticket", "工单管理"),
    ("CampaignCreate", "campaign", "活动创建"),
    ("ComplaintManage", "complaint", "投诉管理"),
    ("ReviewManage", "review-manage", "评价管理"),
    ("ProductAnalysis", "product-analysis", "商品分析"),
    ("ProductReport", "product-report", "商品报表"),
    ("ProductReviewManage", "product-review", "商品评价审核"),
    ("PriceManage", "price-manage", "价格管理"),
    ("PriceHistory", "price-history", "价格历史"),
    ("OrderExport", "order-export", "订单导出"),
    ("OrderIntercept", "order-intercept", "订单拦截"),
    ("OrderMonitor", "order-monitor", "订单监控"),
    ("OrderPriceModify", "order-price-modify", "订单改价"),
    ("OrderPrint", "order-print", "订单打印"),
    ("SmsManage", "sms", "短信管理"),
    ("SensitiveWords", "sensitive-words", "敏感词管理"),
    ("FunnelAnalysis", "funnel", "漏斗分析"),
    ("RfmAnalysis", "rfm", "RFM分析"),
    ("RiskControl", "risk-control", "风控管理"),
    ("RiskRuleEngine", "risk-rule-engine", "风控规则引擎"),
    ("RealtimeScreen", "realtime-screen", "实时大屏"),
    ("UserProfile", "user-profile", "用户画像"),
    ("AbTest", "ab-test", "A/B测试"),
    ("AppVersion", "app-version", "应用版本管理"),
    ("BatchImport", "batch-import", "批量导入"),
    ("KnowledgeBase", "knowledge-base", "知识库"),
    ("SearchAnalysis", "search-analysis", "搜索分析"),
    ("TrafficAnalysis", "traffic-analysis", "流量分析"),
    ("SatisfactionManage", "satisfaction", "满意度管理"),
    ("GdprManage", "gdpr", "GDPR合规"),
    ("AuditLog", "audit-log", "审计日志"),
    ("ProductApproval", "product-approval", "商品审核"),
    ("CouponManage", "coupon-manage", "优惠券管理"),
    ("FlashSaleManage", "flash-sale-manage", "秒杀管理"),
    ("PointsManage", "points-manage", "积分管理"),
    ("BlacklistManage", "blacklist", "黑名单管理"),
    ("TariffManage", "tariff", "关税管理"),
    ("RiskAlert", "risk-alert", "风控告警"),
    ("CsSessions", "cs-sessions", "客服会话"),
    ("OrderTags", "order-tags", "订单标签"),
    ("InventoryTransfer", "inventory-transfer", "库存调拨"),
    ("MergePackage", "merge-package", "合包管理"),
    ("SplitPackage", "split-package", "分包裹"),
    ("CarrierCompare", "carrier-compare", "承运商对比"),
    ("OverseasWarehouse", "overseas-warehouse", "海外仓管理"),
    ("WarehouseManage", "warehouse-manage", "仓库管理"),
    ("ClearanceManage", "clearance", "清关管理"),
    ("CustomsManage", "customs", "海关管理"),
    ("SettlementManage", "settlement", "结算管理"),
    ("SettlementDetail", "settlement-detail", "结算详情"),
    ("SystemConfig", "system-config", "系统配置"),
    ("OperationLog", "operation-log", "运营日志"),
    ("LiveManage", "live-manage", "直播管理"),
    ("MarketingEffect", "marketing-effect", "营销效果"),
    ("ShippingStrategy", "shipping-strategy", "发货策略"),
    ("ContentReviewDetail", "content-review-detail", "内容审核详情"),
    ("PushDetail", "push-detail", "推送详情"),
    ("ComplaintHandle", "complaint-handle", "投诉处理详情"),
]


def test_pages(browser):
    results = []
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()

    # 收集错误
    all_errors = []
    all_api_failures = []

    def on_console(msg):
        if msg.type == "error":
            all_errors.append({"text": msg.text[:200]})

    def on_pageerror(exc):
        all_errors.append({"text": str(exc)[:200]})

    def on_response(resp):
        if "/api/" in resp.url and resp.status >= 400:
            all_api_failures.append({"url": resp.url, "status": resp.status, "page": page.url})

    page.on("console", on_console)
    page.on("pageerror", on_pageerror)
    page.on("response", on_response)

    # 登录
    print("正在登录...")
    page.goto(f"{BASE}/admin/login", wait_until="domcontentloaded", timeout=30000)
    page.wait_for_load_state("networkidle", timeout=15000)
    try:
        page.fill('input[type="email"], input[placeholder*="邮"], input[placeholder*="mail"]', "admin@moyuyo.com", timeout=5000)
        page.fill('input[type="password"]', "123456", timeout=5000)
        page.click('button:has-text("登录"), button[type="submit"]', timeout=5000)
        page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
    except Exception as e:
        print(f"登录失败: {e}")
        page.screenshot(path=str(SCREENSHOT_DIR / "login_failed.png"))
        return []

    # 登录后对详情/编辑页动态获取真实 ID（替换 __ID__ 占位符）
    def resolve_path(entry):
        name, path, desc = entry[0], entry[1], entry[2]
        # 元组格式: (name, path, desc, "fetch_first_id", api_url, id_field) — 共 6 元素
        if "__ID__" in path and len(entry) >= 6 and entry[3] == "fetch_first_id":
            api_url, id_field = entry[4], entry[5]
            try:
                # 先获取列表中的前几个 ID
                ids = page.evaluate(
                    """async (args) => {
                        const [apiUrl, idField] = args;
                        const res = await fetch(apiUrl, {
                            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }
                        });
                        const body = await res.json();
                        const data = body && body.data;
                        if (data) {
                            const records = data.records || data.list || data;
                            if (Array.isArray(records)) {
                                return records.slice(0, 5).map(r => r[idField]);
                            }
                        }
                        return [];
                    }""",
                    [api_url, id_field]
                )
                # 依次尝试，找到一个详情可查的 ID
                for test_id in ids:
                    if test_id is None:
                        continue
                    ok = page.evaluate(
                        """async (testId) => {
                            const res = await fetch('/api/admin/' + (testId > 1000000 && testId > 9999999999 ? 'products/' : 'orders/') + testId, {
                                headers: { 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }
                            });
                            // 兼容：分别试 orders 和 products
                            return null;
                        }""",
                        test_id
                    )
                    # 简化：直接拼路径
                    base_path = "/api/admin/orders/" if "orders" in api_url else "/api/admin/products/"
                    detail_ok = page.evaluate(
                        """async (args) => {
                            const [basePath, testId] = args;
                            const res = await fetch(basePath + testId, {
                                headers: { 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }
                            });
                            const body = await res.json();
                            return body && body.code === 0;
                        }""",
                        [base_path, test_id]
                    )
                    if detail_ok:
                        path = path.replace("__ID__", str(test_id))
                        print(f"  -> {name} 动态ID={test_id}")
                        return name, path, desc
                # 所有 ID 都不可查
                path = path.replace("__ID__", "1")
                print(f"  -> {name} 所有候选ID详情不可查，回退到 1")
            except Exception as e:
                path = path.replace("__ID__", "1")
                print(f"  -> {name} 动态ID获取失败: {e}")
        return name, path, desc

    # 访问每个页面
    for entry in PAGES:
        name, path, desc = resolve_path(entry)
        url = f"{BASE}/admin/{path}"
        before_errors = len(all_errors)
        before_api = len(all_api_failures)
        start = time.time()
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=20000)
            try:
                page.wait_for_load_state("networkidle", timeout=8000)
            except PlaywrightTimeout:
                pass
            time.sleep(0.4)
            elapsed = round((time.time() - start) * 1000)

            screenshot = SCREENSHOT_DIR / f"{name}.png"
            try:
                page.screenshot(path=str(screenshot), full_page=False)
            except Exception:
                pass

            body_text = page.evaluate("() => document.body.innerText.substring(0, 300)")

            new_errors = all_errors[before_errors:]
            new_api_failures = [f for f in all_api_failures[before_api:] if f["page"].endswith(path)]

            status = "OK"
            issues = []
            # 跳到 NotFound 算失败
            if "NotFound" in body_text and "NotFound" not in name:
                status = "404"
                issues.append("页面未找到")
            if new_errors:
                critical = [e for e in new_errors if "Warning" not in e["text"] and "[Vue warn]" not in e["text"]]
                if critical:
                    issues.append(f"JS错误 {len(critical)}")
                    if status == "OK":
                        status = "WARN"
            if new_api_failures:
                issues.append(f"API失败 {len(new_api_failures)}")
                if status == "OK":
                    status = "WARN"

            result = {
                "name": name,
                "path": path,
                "desc": desc,
                "status": status,
                "elapsed_ms": elapsed,
                "issues": issues,
                "api_failures": [{"url": f["url"][-60:], "status": f["status"]} for f in new_api_failures[:3]],
                "js_errors": [e["text"][:100] for e in new_errors[:3]],
                "screenshot": str(screenshot),
            }
            tag = "OK  " if status == "OK" else status
            print(f"[{tag}] {name:<28} {elapsed:>5}ms {path:<35} {','.join(issues) or ''}")
        except Exception as e:
            result = {
                "name": name,
                "path": path,
                "desc": desc,
                "status": "ERROR",
                "error": str(e)[:200],
            }
            print(f"[ERR] {name:<28} - {str(e)[:80]}")
        results.append(result)

    context.close()
    return results


def main():
    with sync_playwright() as p:
        # 使用系统 Chrome
        browser = p.chromium.launch(
            headless=True,
            executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            args=["--no-sandbox", "--disable-dev-shm-usage"],
        )
        results = test_pages(browser)
        browser.close()

    total = len(results)
    ok = sum(1 for r in results if r["status"] == "OK")
    warn = sum(1 for r in results if r["status"] == "WARN")
    err = sum(1 for r in results if r["status"] in ("ERROR", "404"))

    summary = {
        "scan_time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total": total,
        "ok": ok,
        "warn": warn,
        "err": err,
        "results": results,
    }
    OUTPUT.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n汇总: 总 {total} | OK {ok} | WARN {warn} | ERR {err}")
    print(f"报告: {OUTPUT}")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
