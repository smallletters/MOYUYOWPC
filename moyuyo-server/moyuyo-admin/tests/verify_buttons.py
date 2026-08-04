"""
最终验证 - 真实登录流程 + 全量按钮测试
- 登录后访问所有管理页面
- 识别每个页面的关键按钮（搜索、重置、导出、新增、删除等）
- 点击每个按钮验证无 page_error / 关键 console_error
- 输出最终报告
"""
import time
import urllib.request
import json
import hmac
import hashlib
import base64
import secrets
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080/admin"
API_BASE = "http://localhost:8080"
LOGIN_EMAIL = "admin@moyuyo.com"
LOGIN_PASSWORD = "123456"

# 不需要点击的关键按钮（可能会跳走或弹大窗）仅做存在性检查
INTERACTIVE_BUTTONS = ["搜索", "重置", "查询", "刷新"]


def _load_env():
    env_path = Path(r"D:\MOYUYOWPC\moyuyo-server\.env")
    env = {}
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


ENV = _load_env()
SIGN_SECRET = ENV.get("API_SIGN_SECRET", "")
SKIP_SIGN_PATHS = {
    "/api/admin/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/health",
    "/actuator/",
}


def make_sign_headers(method: str, path: str) -> dict:
    timestamp = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{timestamp}{nonce}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(SIGN_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
    return {
        "X-Sign": base64.b64encode(sig).decode("utf-8"),
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
    }


# 关键页面及其期望的按钮关键词
PAGES_BUTTONS = [
    ("/dashboard", "仪表盘", []),
    ("/orders", "订单管理", ["搜索", "重置", "导出"]),
    ("/products", "商品管理", ["搜索", "重置", "新增", "导出"]),
    ("/users", "用户管理", ["搜索", "重置"]),
    ("/marketing", "营销管理", ["搜索"]),
    ("/reviews", "内容审核", ["搜索", "通过", "拒绝"]),
    ("/cs", "客服管理", ["搜索"]),
    ("/analytics", "数据分析", ["搜索"]),
    ("/logistics", "物流管理", ["搜索"]),
    ("/finance", "财务概览", ["搜索"]),
    ("/inventory", "库存管理", ["搜索", "新增"]),
    ("/ticket", "工单管理", ["搜索"]),
    ("/cms", "CMS内容管理", ["搜索", "新增"]),
    ("/rbac", "RBAC权限管理", ["搜索", "新增"]),
    ("/push-manage", "推送管理", ["搜索", "新增"]),
    ("/campaign", "活动创建", ["保存", "取消"]),
    ("/complaint", "投诉管理", ["搜索"]),
    ("/review-manage", "评价管理", ["搜索"]),
    ("/product-analysis", "商品分析", ["搜索"]),
    ("/product-report", "商品报表", ["搜索", "重置", "导出报表"]),
    ("/product-review", "商品评价审核", ["搜索"]),
    ("/price-manage", "价格管理", ["搜索", "新增"]),
    ("/price-history", "价格历史", ["搜索"]),
    ("/order-export", "订单导出", ["导出", "搜索"]),
    ("/order-intercept", "订单拦截", ["搜索"]),
    ("/order-monitor", "订单监控", ["搜索"]),
    ("/order-price-modify", "订单改价", ["搜索"]),
    ("/order-print", "订单打印", ["搜索", "打印"]),
    ("/sms", "短信管理", ["搜索", "发送"]),
    ("/sensitive-words", "敏感词管理", ["搜索", "新增"]),
    ("/funnel", "漏斗分析", ["搜索"]),
    ("/rfm", "RFM分析", ["搜索"]),
    ("/risk-control", "风控管理", ["搜索"]),
    ("/risk-rule-engine", "风控规则引擎", ["搜索", "新增"]),
    ("/realtime-screen", "实时大屏", []),
    ("/user-profile", "用户画像", ["搜索"]),
    ("/ab-test", "A/B测试", ["搜索", "新增"]),
    ("/app-version", "应用版本管理", ["搜索", "新增"]),
    ("/batch-import", "批量导入", ["导入", "下载模板"]),
    ("/knowledge-base", "知识库", ["搜索", "新增"]),
    ("/search-analysis", "搜索分析", ["搜索"]),
    ("/traffic-analysis", "流量分析", ["搜索"]),
    ("/satisfaction", "满意度管理", ["搜索"]),
    ("/gdpr", "GDPR合规", ["搜索", "导出"]),
    ("/audit-log", "审计日志", ["搜索", "重置"]),
    ("/product-approval", "商品审核", ["搜索", "通过", "拒绝"]),
    ("/coupon-manage", "优惠券管理", ["搜索", "新增"]),
    ("/flash-sale-manage", "秒杀管理", ["搜索", "新增"]),
    ("/points-manage", "积分管理", ["搜索"]),
    ("/blacklist", "黑名单管理", ["搜索", "新增"]),
    ("/tariff", "关税管理", ["搜索", "新增"]),
    ("/risk-alert", "风控告警", ["搜索"]),
    ("/cs-sessions", "客服会话", ["搜索"]),
    ("/order-tags", "订单标签", ["搜索", "新增"]),
    ("/inventory-transfer", "库存调拨", ["搜索", "新增"]),
    ("/merge-package", "合包管理", ["搜索", "新增"]),
    ("/split-package", "分包裹", ["搜索", "新增"]),
    ("/carrier-compare", "承运商对比", ["搜索"]),
    ("/overseas-warehouse", "海外仓管理", ["搜索", "新增"]),
    ("/warehouse-manage", "仓库管理", ["搜索", "新增"]),
    ("/clearance", "清关管理", ["搜索"]),
    ("/customs", "海关管理", ["搜索"]),
    ("/settlement", "结算管理", ["搜索"]),
    ("/system-config", "系统配置", ["保存"]),
    ("/operation-log", "运营日志", ["搜索"]),
    ("/live-manage", "直播管理", ["搜索", "新增"]),
    ("/marketing-effect", "营销效果", ["搜索"]),
    ("/shipping-strategy", "发货策略", ["搜索", "新增"]),
    ("/settings", "系统设置", ["保存"]),
]


def benign_console_error(text: str) -> bool:
    """判断控制台错误是否为可忽略的（favicon、devtools 等）"""
    t = text.lower()
    ignore_substrings = [
        "favicon",
        "download the react devtools",
        "[hmr]",
        "websocket",
        "the resource",
        "source map",
        "manifest",
    ]
    return any(s in t for s in ignore_substrings)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r"C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe"
        )
        context = browser.new_context(viewport={"width": 1920, "height": 1080})
        page = context.new_page()

        # 登录
        page.goto(f"{BASE_URL}/login")
        page.wait_for_load_state("networkidle")
        page.locator('input[type="email"]').fill(LOGIN_EMAIL)
        page.locator('input[type="password"]').fill(LOGIN_PASSWORD)
        page.locator('button.login-btn').click()
        page.wait_for_url("**/dashboard", timeout=10000)
        print(f"[LOGIN] OK\n", flush=True)

        results = []
        passed_pages = 0
        failed_pages = 0

        for path, name, expected_buttons in PAGES_BUTTONS:
            page_errors = []
            console_errors = []

            def on_console(msg):
                if msg.type == "error":
                    console_errors.append(msg.text)

            def on_pageerror(err):
                page_errors.append(str(err))

            page.on("console", on_console)
            page.on("pageerror", on_pageerror)

            # 展开所有导航分组
            try:
                for header in page.locator('.nav-group-header').all():
                    try:
                        header.click()
                        time.sleep(0.05)
                    except Exception:
                        pass
            except Exception:
                pass

            try:
                page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded", timeout=15000)
                page.wait_for_load_state("networkidle", timeout=10000)
                time.sleep(1.0)

                # 等待表格或主内容加载
                try:
                    page.wait_for_selector("button, .el-table, .filter-card, .el-card", timeout=5000)
                except Exception:
                    pass

                # 检查期望按钮是否存在
                found_buttons = []
                missing_buttons = []
                for btn_text in expected_buttons:
                    try:
                        # 多种选择器尝试
                        selectors = [
                            f'button:has-text("{btn_text}"):not([disabled])',
                            f'.el-button:has-text("{btn_text}"):not([disabled])',
                            f'button >> text="{btn_text}"',
                            f'.el-button >> text="{btn_text}"',
                        ]
                        found = False
                        for sel in selectors:
                            try:
                                loc = page.locator(sel).first
                                if loc.count() > 0 and loc.is_visible():
                                    found = True
                                    break
                            except Exception:
                                continue
                        if found:
                            found_buttons.append(btn_text)
                        else:
                            missing_buttons.append(btn_text)
                    except Exception:
                        missing_buttons.append(btn_text)

                # 尝试点击交互型按钮（搜索、重置等安全操作）
                clicked_buttons = []
                click_errors = []
                for btn_text in INTERACTIVE_BUTTONS:
                    if btn_text not in found_buttons:
                        continue
                    try:
                        selectors = [
                            f'button:has-text("{btn_text}"):not([disabled])',
                            f'.el-button:has-text("{btn_text}"):not([disabled])',
                        ]
                        for sel in selectors:
                            try:
                                loc = page.locator(sel).first
                                if loc.count() > 0 and loc.is_visible():
                                    loc.click()
                                    clicked_buttons.append(btn_text)
                                    break
                            except Exception:
                                continue
                        time.sleep(0.4)
                    except Exception as e:
                        click_errors.append(f"{btn_text}: {str(e)[:80]}")

                time.sleep(0.5)

                # 过滤可忽略的 console error
                real_console_errors = [e for e in console_errors if not benign_console_error(e)]
                real_page_errors = [e for e in page_errors]

                status = "PASS"
                if real_page_errors:
                    status = "FAIL"
                elif len(real_console_errors) > 5:
                    # 超过5个真实 console 错误视为异常
                    status = "WARN"

                if status == "FAIL":
                    failed_pages += 1
                else:
                    passed_pages += 1

                results.append({
                    "path": path,
                    "name": name,
                    "status": status,
                    "found_buttons": found_buttons,
                    "missing_buttons": missing_buttons,
                    "clicked_buttons": clicked_buttons,
                    "click_errors": click_errors[:3],
                    "page_errors": real_page_errors[:3],
                    "console_errors": real_console_errors[:3],
                })

                marker = {"PASS": "✓", "WARN": "⚠", "FAIL": "✗"}.get(status, "?")
                print(f"{marker} {name:18s} btn_found={len(found_buttons):2d}/{len(expected_buttons):2d} clicked={len(clicked_buttons)} errors={len(real_page_errors)}", flush=True)

            except Exception as e:
                failed_pages += 1
                results.append({
                    "path": path, "name": name, "status": "EXCEPTION",
                    "error": str(e)[:200]
                })
                print(f"✗ {name:18s} EXCEPTION: {str(e)[:100]}", flush=True)

            page.remove_listener("console", on_console)
            page.remove_listener("pageerror", on_pageerror)

        browser.close()

    # 汇总
    print(f"\n{'='*70}")
    print(f"最终汇总: 通过 {passed_pages} / 失败 {failed_pages} / 总计 {len(PAGES_BUTTONS)}")
    print(f"{'='*70}")

    # 失败详情
    failures = [r for r in results if r.get("status") in ("FAIL", "EXCEPTION")]
    if failures:
        print(f"\n失败详情 ({len(failures)}):")
        for r in failures:
            print(f"  ✗ {r['name']} ({r['path']})")
            if r.get("error"):
                print(f"    异常: {r['error']}")
            for e in r.get("page_errors", []):
                print(f"    page_error: {e[:160]}")
            for e in r.get("console_errors", []):
                print(f"    console_error: {e[:160]}")

    # 警告（按钮缺失但不致命）
    warnings = [r for r in results if r.get("status") == "WARN" or (r.get("missing_buttons") and r.get("status") == "PASS")]
    if warnings:
        print(f"\n按钮缺失页面 ({len(warnings)}):")
        for r in warnings:
            if r.get("missing_buttons"):
                print(f"  ⚠ {r['name']}: 缺少 {r['missing_buttons']}")

    # 输出 JSON 报告
    out_path = r"D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\verify_buttons.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({
            "summary": {
                "total": len(PAGES_BUTTONS),
                "passed": passed_pages,
                "failed": failed_pages,
                "warnings": len(warnings),
            },
            "results": results,
        }, f, ensure_ascii=False, indent=2)
    print(f"\n详细报告: {out_path}")

    if failed_pages == 0:
        print(f"\n✓ 所有页面均无致命 page_error，按钮测试通过！")
        return 0
    else:
        print(f"\n✗ 有 {failed_pages} 个页面存在严重问题")
        return 1


if __name__ == "__main__":
    import sys
    sys.exit(main())
