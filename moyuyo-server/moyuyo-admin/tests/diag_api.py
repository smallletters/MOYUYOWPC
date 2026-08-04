"""精确诊断：检查每个API的失败原因"""
import time
import json
import urllib.request
import urllib.error
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080/admin"
API_BASE = "http://localhost:8080"
LOGIN_EMAIL = "admin@moyuyo.com"
LOGIN_PASSWORD = "123456"

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
    print(f"[LOGIN] OK\n")

    # 等待 dashboard 加载
    time.sleep(2)

    # 拦截所有 API 请求
    api_responses = []
    def on_response(response):
        if '/api/admin' in response.url and 'auth/login' not in response.url:
            try:
                body = response.text() if response.status != 200 else "(200 OK)"
            except Exception:
                body = "(无法读取)"
            api_responses.append({
                'status': response.status,
                'method': response.request.method,
                'url': response.url,
                'body': body[:300] if body else "",
            })
    page.on('response', on_response)

    # 触发 dashboard 加载
    page.goto(f"{BASE_URL}/dashboard", wait_until="domcontentloaded")
    page.wait_for_load_state("networkidle", timeout=10000)
    time.sleep(3)

    print(f"\nDashboard 加载后 API 响应:")
    for r in api_responses:
        print(f"  [{r['status']}] {r['method']} {r['url']}")
        if r['status'] != 200:
            print(f"    body: {r['body']}")

    # 尝试访问商品报表
    api_responses.clear()
    page.goto(f"{BASE_URL}/product-report", wait_until="domcontentloaded")
    page.wait_for_load_state("networkidle", timeout=10000)
    time.sleep(3)
    print(f"\n商品报表页 API 响应:")
    for r in api_responses:
        print(f"  [{r['status']}] {r['method']} {r['url']}")
        if r['status'] != 200:
            print(f"    body: {r['body']}")

    # 当前URL
    print(f"\n当前URL: {page.url}")

    # 截图
    page.screenshot(path="D:/MOYUYOWPC/moyuyo-server/moyuyo-admin/tests/diag_full.png", full_page=True)

    browser.close()
