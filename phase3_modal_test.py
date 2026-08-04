# -*- coding: utf-8 -*-
"""阶段3 - 弹窗与按钮交互测试（Playwright）
对代表性页面执行：打开新增/编辑弹窗 -> 取消关闭 -> 删除确认框 -> 取消
同时检测页面上的"功能开发中"占位提示
"""
import json
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/admin"
API_BASE = "http://127.0.0.1:8080/api/admin"
OUTPUT = Path(r"D:\MOYUYOWPC\phase3_modal_result.json")


def get_api_token():
    """通过后端 API 获取登录 token"""
    import urllib.request
    import urllib.error
    body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
    req = urllib.request.Request(f"{API_BASE}/auth/login", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            payload = json.loads(r.read().decode("utf-8"))
            if payload.get("code") == 0:
                return payload["data"]["token"]
    except Exception:
        pass
    return None

# (名称, 路由, 期望弹窗触发按钮关键词)
PAGES = [
    ("CmsManage", "cms", ["新建"]),
    ("OrderList", "orders", ["筛选", "查询", "重置"]),
    ("ProductList", "products", ["新增", "新建"]),
    ("UserList", "users", ["新增", "新建"]),
    ("MarketingList", "marketing", ["新建", "新增", "创建"]),
    ("CustomerService", "cs", ["回复", "分配"]),
    ("RbacManage", "rbac", ["新增", "新建"]),
    ("CouponManage", "coupon-manage", ["新增", "新建"]),
    ("SmsManage", "sms", ["发送"]),
    ("RiskControl", "risk-control", ["新增", "新建"]),
]


def main():
    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        ctx = browser.new_context(viewport={"width": 1600, "height": 900})
        page = ctx.new_page()
        console_errors = []
        page.on("console", lambda msg: console_errors.append(msg.text) if msg.type == "error" else None)

        # 登录（通过 API 获取 token 注入 localStorage，避免 UI 选择器问题）
        token = get_api_token()
        if not token:
            results.append({"page": "Login", "result": "登录失败", "detail": "无法获取 token"})
            browser.close()
            write_output(results)
            return
        page.goto(f"{BASE}/login", wait_until="domcontentloaded")
        page.wait_for_timeout(600)
        page.evaluate(f"localStorage.setItem('admin_token', '{token}')")
        page.wait_for_timeout(300)
        page.goto(f"{BASE}/dashboard", wait_until="domcontentloaded")
        page.wait_for_timeout(1200)
        print(f"登录后 URL: {page.url}")

        for name, route, keywords in PAGES:
            console_errors.clear()
            page.goto(f"{BASE}/{route}", wait_until="networkidle")
            page.wait_for_timeout(1200)
            record = {
                "page": name,
                "route": route,
                "url": page.url,
                "console_errors": list(console_errors),
                "modals": [],
                "todo_placeholders": [],
                "result": "OK",
            }

            # 检测"功能开发中"占位
            todo = page.locator("text=/功能开发中|开发中，请联系管理员|暂未开放|即将上线/")
            if todo.count() > 0:
                record["todo_placeholders"] = [t.strip()[:60] for t in todo.all_text_contents()[:5]]
                record["result"] = "发现未实现功能占位"

            # 尝试点击关键词按钮打开弹窗
            for kw in keywords:
                btns = page.locator(f"button:has-text('{kw}')")
                n = btns.count()
                if n == 0:
                    continue
                for i in range(min(n, 2)):
                    btn = btns.nth(i)
                    try:
                        btn.scroll_into_view_if_needed(timeout=2000)
                        btn.click(timeout=3000)
                        page.wait_for_timeout(600)
                    except Exception as e:
                        record["modals"].append({"button": kw, "click": f"点击失败: {str(e)[:100]}"})
                        continue
                    # 检查弹窗是否出现（el-dialog / el-overlay / MessageBox）
                    dialog = page.locator(".el-dialog:visible, .el-overlay:visible")
                    opened = dialog.count() > 0
                    # 收集弹窗标题/内容
                    dlg_info = ""
                    if opened:
                        try:
                            dlg_info = dialog.first.inner_text()[:120].replace("\n", " ")
                        except Exception:
                            pass
                    # 尝试关闭：先点"取消"再按 Esc
                    closed = False
                    if opened:
                        cancel_btn = page.locator(".el-dialog:visible button:has-text('取 消'), .el-dialog:visible button:has-text('取消')")
                        if cancel_btn.count() > 0:
                            try:
                                cancel_btn.first.click(timeout=2000)
                                page.wait_for_timeout(500)
                                closed = page.locator(".el-dialog:visible, .el-overlay:visible").count() == 0
                            except Exception:
                                closed = False
                        if not closed:
                            try:
                                page.keyboard.press("Escape")
                                page.wait_for_timeout(400)
                                closed = page.locator(".el-dialog:visible, .el-overlay:visible").count() == 0
                            except Exception:
                                pass
                    record["modals"].append({
                        "button": kw,
                        "opened": opened,
                        "dialog_info": dlg_info,
                        "closed": closed,
                        "click": "OK",
                    })
                    # 若弹窗已打开又关闭则继续下一个按钮
                    if opened and closed:
                        continue

            # 删除确认框检测（若页面有删除按钮）
            del_btn = page.locator("button:has-text('删除')").first
            if del_btn.count() > 0:
                try:
                    del_btn.scroll_into_view_if_needed(timeout=2000)
                    del_btn.click(timeout=3000)
                    page.wait_for_timeout(500)
                    confirm = page.locator(".el-message-box:visible, .el-overlay-message-box:visible")
                    has_confirm = confirm.count() > 0
                    if has_confirm:
                        # 点击取消按钮
                        mb = page.locator(".el-message-box:visible button:has-text('取 消'), .el-message-box:visible button:has-text('取消')")
                        if mb.count() > 0:
                            mb.first.click()
                            page.wait_for_timeout(400)
                        record["delete_confirm"] = {"opened": True, "closed": page.locator(".el-message-box:visible").count() == 0}
                    else:
                        record["delete_confirm"] = {"opened": False, "note": "点击删除未出现确认框（可能直接删除或需选择行）"}
                except Exception as e:
                    record["delete_confirm"] = {"opened": False, "note": str(e)[:100]}

            if record["result"] == "OK" and not record["console_errors"]:
                record["result"] = "OK"
            results.append(record)
            print(f"[{record['result']:16s}] {name} ({route}) 弹窗交互 {len(record['modals'])} 项")

        browser.close()

    write_output(results)
    # 汇总
    errs = [r for r in results if r["console_errors"]]
    todos = [r for r in results if r["todo_placeholders"]]
    print(f"\n=== 汇总: {len(results)} 页, 控制台错误 {len(errs)} 页, 未实现占位 {len(todos)} 页 ===")
    for r in todos:
        print(f"  [占位] {r['page']}: {r['todo_placeholders']}")


def write_output(results):
    with open(OUTPUT, "w", encoding="utf-8") as f:
        json.dump({"scan_time": time.strftime("%Y-%m-%d %H:%M:%S"), "results": results},
                  f, ensure_ascii=False, indent=2)
    print(f"结果已写入 {OUTPUT}")


if __name__ == "__main__":
    main()
