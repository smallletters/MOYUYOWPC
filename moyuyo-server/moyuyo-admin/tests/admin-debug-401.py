from playwright.sync_api import sync_playwright
import time

BASE_URL = 'http://localhost:8080/admin'

def log_request(request):
    headers = request.headers
    auth = headers.get('authorization', '')
    print(f"[REQ] {request.method} {request.url} Authorization={auth[:50] if auth else 'NONE'}")

def log_response(response):
    if response.status >= 400:
        print(f"[RES] {response.status} {response.url}")

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
    )
    page = browser.new_page(viewport={'width': 1920, 'height': 1080})
    page.on('request', log_request)
    page.on('response', log_response)
    page.on('console', lambda msg: print(f"[CONSOLE {msg.type}] {msg.text}"))

    # 直接访问 dashboard（未登录状态）
    print("=== 测试1: 未登录直接访问 dashboard ===")
    page.goto(f'{BASE_URL}/dashboard')
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    print(f"当前URL: {page.url}")
    page.screenshot(path='D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\debug_unauthorized.png', full_page=True)

    # 登录
    print("\n=== 测试2: 登录后访问 dashboard ===")
    page.goto(f'{BASE_URL}/login')
    page.wait_for_load_state('networkidle')
    page.locator('input[type="email"]').fill('admin@moyuyo.com')
    page.locator('input[type="password"]').fill('123456')
    page.locator('button.login-btn').click()
    page.wait_for_url('**/dashboard')
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    print(f"登录后URL: {page.url}")
    token = page.evaluate("() => localStorage.getItem('admin_token')")
    print(f"localStorage token: {token[:60]}..." if token and len(token) > 60 else f"localStorage token: {token}")
    page.screenshot(path='D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\debug_authorized.png', full_page=True)

    browser.close()
