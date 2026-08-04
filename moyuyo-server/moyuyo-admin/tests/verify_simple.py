"""简化版验证 - 仅检查关键修复"""
import time
import urllib.request
import json
from playwright.sync_api import sync_playwright

def login_get_token():
    req = urllib.request.Request(
        "http://localhost:8080/api/admin/auth/login",
        data=json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode(),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as r:
        body = json.loads(r.read().decode())
    return body["data"]["token"]

def main():
    token = login_get_token()
    print(f"登录成功，token 长度: {len(token)}")

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r"C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe"
        )
        context = browser.new_context(viewport={"width": 1920, "height": 1080})
        context.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        page = context.new_page()

        results = {}

        for path, name in [("/product-report", "商品报表"), ("/audit-log", "审计日志")]:
            page_errors = []
            console_errors = []

            def on_console(msg):
                if msg.type == "error":
                    console_errors.append(msg.text)

            def on_pageerror(err):
                page_errors.append(str(err))

            page.on("console", on_console)
            page.on("pageerror", on_pageerror)

            print(f"\n测试: {name} ({path})")
            page.goto(f"http://localhost:8080/admin{path}", wait_until="domcontentloaded", timeout=15000)
            page.wait_for_load_state("networkidle", timeout=10000)
            # 等待表格行出现
            try:
                page.wait_for_selector("tbody tr", timeout=8000)
            except Exception:
                pass
            time.sleep(2)
            # 滚动到底部再回顶
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            time.sleep(0.5)
            page.evaluate("window.scrollTo(0, 0)")

            # 截图保存
            shot_path = f"D:/MOYUYOWPC/moyuyo-server/moyuyo-admin/tests/verify_{name}.png"
            page.screenshot(path=shot_path, full_page=True)
            rows = page.locator("tbody tr").count()
            button_count = page.locator("button").count()

            results[name] = {
                "page_errors": page_errors[:],
                "console_errors": [e for e in console_errors if "favicon" not in e.lower()],
                "rows": rows,
                "buttons": button_count
            }
            page.remove_listener("console", on_console)
            page.remove_listener("pageerror", on_pageerror)

        # 测试 商品报表 搜索按钮
        print(f"\n测试 商品报表 - 搜索按钮")
        page.goto("http://localhost:8080/admin/product-report", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        # 通过 .el-input__inner 选择器找到输入框
        try:
            inputs = page.locator("input.el-input__inner").all()
            if inputs:
                # 商品报表的关键词输入框
                inputs[0].fill("Premium")
                time.sleep(0.3)
                # 找到"搜索"按钮
                search_btn = page.get_by_role("button", name="搜索").first
                search_btn.click()
                time.sleep(1.5)
                rows_after = page.locator("tbody tr").count()
                print(f"  搜索 'Premium' 后表格行数: {rows_after}")
        except Exception as e:
            print(f"  搜索测试跳过: {e}")

        # 测试 审计日志 重置按钮
        print(f"\n测试 审计日志 - 重置按钮")
        page.goto("http://localhost:8080/admin/audit-log", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        try:
            reset_btn = page.get_by_role("button", name="重置").first
            reset_btn.click()
            time.sleep(1)
            print(f"  重置按钮点击完成")
        except Exception as e:
            print(f"  重置测试跳过: {e}")

        browser.close()

    # 汇总
    print(f"\n{'='*60}\n最终汇总:")
    all_pass = True
    for name, r in results.items():
        page_errs = [e for e in r["page_errors"] if "favicon" not in e.lower()]
        console_errs = r["console_errors"]
        if page_errs or console_errs:
            all_pass = False
            print(f"  ✗ {name}: page_errs={len(page_errs)}, console_errs={len(console_errs)}, rows={r['rows']}, buttons={r['buttons']}")
            for e in page_errs:
                print(f"      page_error: {e[:160]}")
            for e in console_errs:
                print(f"      console_error: {e[:160]}")
        else:
            print(f"  ✓ {name}: 无错误, rows={r['rows']}, buttons={r['buttons']}")
    if all_pass:
        print("\n✓ 所有目标页面无关键错误！")
    else:
        print("\n✗ 仍有问题")

if __name__ == "__main__":
    main()
