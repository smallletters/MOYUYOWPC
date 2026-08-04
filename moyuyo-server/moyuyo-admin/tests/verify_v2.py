"""改进版验证 - 通过页面 evaluate 注入 token"""
import time
import urllib.request
import json
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080/admin"

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
        # 直接通过 storage_state 注入 token（origin 必须匹配 BASE_URL）
        from urllib.parse import urlparse
        origin = urlparse(BASE_URL).scheme + "://" + urlparse(BASE_URL).netloc
        storage_state = {
            "origins": [
                {
                    "origin": origin,
                    "localStorage": [
                        {"name": "admin_token", "value": token}
                    ]
                }
            ],
            "cookies": []
        }
        context = browser.new_context(
            viewport={"width": 1920, "height": 1080},
            storage_state=storage_state
        )
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
            page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded", timeout=15000)
            page.wait_for_load_state("networkidle", timeout=10000)
            try:
                page.wait_for_selector("tbody tr", timeout=8000)
            except Exception:
                pass
            time.sleep(2)
            # 滚动一遍
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
            time.sleep(0.3)
            page.evaluate("window.scrollTo(0, 0)")

            shot_path = f"D:/MOYUYOWPC/moyuyo-server/moyuyo-admin/tests/v2_{name}.png"
            page.screenshot(path=shot_path, full_page=True)
            rows = page.locator("tbody tr").count()
            button_count = page.locator("button").count()

            # 过滤 favicon 错误
            filtered_console = [e for e in console_errors if "favicon" not in e.lower()]

            results[name] = {
                "page_errors": page_errors[:],
                "console_errors": filtered_console,
                "rows": rows,
                "buttons": button_count
            }
            page.remove_listener("console", on_console)
            page.remove_listener("pageerror", on_pageerror)

        # 测试 商品报表 搜索按钮
        print(f"\n测试 商品报表 - 搜索按钮")
        page.goto(f"{BASE_URL}/product-report", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        try:
            inputs = page.locator("input.el-input__inner").all()
            if len(inputs) >= 1:
                inputs[0].fill("Premium")
                time.sleep(0.3)
                # 通过 button text 查找
                page.get_by_text("搜索", exact=True).first.click()
                time.sleep(1.5)
                rows_after = page.locator("tbody tr").count()
                print(f"  搜索 'Premium' 后表格行数: {rows_after}")
        except Exception as e:
            print(f"  搜索测试跳过: {e}")

        # 测试 审计日志 重置按钮
        print(f"\n测试 审计日志 - 重置按钮")
        page.goto(f"{BASE_URL}/audit-log", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        try:
            page.get_by_text("重置", exact=True).first.click()
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
        if page_errs:
            all_pass = False
            print(f"  ✗ {name}: {len(page_errs)} page_errors, rows={r['rows']}, buttons={r['buttons']}")
            for e in page_errs:
                print(f"      page_error: {e[:200]}")
        else:
            print(f"  ✓ {name}: 无 page_error, rows={r['rows']}, buttons={r['buttons']}")
    if all_pass:
        print("\n✓ 所有目标页面无 page_error！")
    else:
        print("\n✗ 仍有问题")

if __name__ == "__main__":
    main()
