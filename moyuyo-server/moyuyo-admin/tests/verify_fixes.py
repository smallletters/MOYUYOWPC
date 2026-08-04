"""验证 ProductReport 和 AuditLog 页面修复"""
import time
import urllib.request
import json
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080/admin"

def login_get_token():
    """获取登录 token"""
    req = urllib.request.Request(
        "http://localhost:8080/api/admin/auth/login",
        data=json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode(),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as r:
        body = json.loads(r.read().decode())
    return body["data"]["token"]

def test_page(page, path, name, expect_console_clean=True):
    """测试单个页面"""
    page_errors = []
    console_errors = []
    network_errors = []

    def on_response(resp):
        if resp.status >= 400 and "/api/" in resp.url:
            network_errors.append(f"{resp.status} {resp.request.method} {resp.url}")

    def on_console(msg):
        if msg.type == "error":
            console_errors.append(msg.text)

    def on_pageerror(err):
        page_errors.append(str(err))

    page.on("response", on_response)
    page.on("console", on_console)
    page.on("pageerror", on_pageerror)

    page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded", timeout=15000)
    page.wait_for_load_state("networkidle", timeout=10000)
    time.sleep(1)

    # 检查表格是否有数据
    table_rows = page.locator("tbody tr").count()
    button_count = page.locator("button").count()

    page.remove_listener("response", on_response)
    page.remove_listener("console", on_console)
    page.remove_listener("pageerror", on_pageerror)

    print(f"\n{'='*60}")
    print(f"页面: {name} ({path})")
    print(f"  表格行数: {table_rows}, 按钮数: {button_count}")
    print(f"  page_errors: {len(page_errors)}")
    for e in page_errors:
        print(f"    - {e[:200]}")
    print(f"  console_errors: {len(console_errors)}")
    for e in console_errors:
        print(f"    - {e[:200]}")
    print(f"  network_errors: {len(network_errors)}")
    for e in network_errors:
        print(f"    - {e[:200]}")

    return {
        "page_errors": page_errors,
        "console_errors": console_errors,
        "network_errors": network_errors,
        "table_rows": table_rows
    }

def main():
    token = login_get_token()
    print(f"登录成功，token 长度: {len(token)}")

    results = {}
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r"C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe"
        )
        context = browser.new_context(viewport={"width": 1920, "height": 1080})
        # 注入 token
        context.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        page = context.new_page()

        for path, name in [
            ("/product/report", "商品报表"),
            ("/audit/log", "审计日志"),
        ]:
            results[name] = test_page(page, path, name)
            time.sleep(0.5)

        # 验证搜索按钮
        print(f"\n{'='*60}\n验证 商品报表 搜索按钮")
        page.goto(f"{BASE_URL}/product/report", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        # 使用 .first 避免多元素问题
        keyword_input = page.locator('input[placeholder*="关键词"]').first
        keyword_input.fill("Premium")
        page.get_by_role("button", name="搜索").first.click()
        time.sleep(2)
        rows = page.locator("tbody tr").count()
        print(f"  搜索 'Premium' 后表格行数: {rows}")

        print(f"\n{'='*60}\n验证 审计日志 重置按钮")
        page.goto(f"{BASE_URL}/audit/log", wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")
        time.sleep(2)
        page.get_by_role("button", name="重置").first.click()
        time.sleep(1)
        print("  重置按钮点击完成，无异常")

        browser.close()

    # 汇总
    print(f"\n{'='*60}\n汇总:")
    all_pass = True
    for name, r in results.items():
        critical = len(r["page_errors"])
        if critical > 0:
            all_pass = False
            print(f"  ✗ {name}: {critical} 个 page_error")
        else:
            print(f"  ✓ {name}: 无 page_error")
    if all_pass:
        print("\n所有测试通过！")
    else:
        print("\n存在未解决问题")

if __name__ == "__main__":
    main()
