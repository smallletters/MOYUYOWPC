from playwright.sync_api import sync_playwright
import time

BASE_URL = 'http://localhost:8080/admin'

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
    )
    page = browser.new_page(viewport={'width': 1920, 'height': 1080})

    # 登录
    page.goto(f'{BASE_URL}/login')
    page.wait_for_load_state('networkidle')
    page.locator('input[type="email"]').fill('admin@moyuyo.com')
    page.locator('input[type="password"]').fill('123456')
    page.locator('button.login-btn').click()
    page.wait_for_url('**/dashboard')
    print(f"登录后URL: {page.url}")

    # 测试1: 点击"商品管理"导航
    page.locator('.nav-item:has-text("商品管理")').click()
    page.wait_for_url('**/products')
    print(f"商品管理URL: {page.url}")

    # 测试2: 点击"+ 新增商品"
    page.locator('button:has-text("新增商品")').click()
    page.wait_for_url('**/products/add')
    print(f"新增商品URL: {page.url}")
    page.screenshot(path='D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\product_add.png', full_page=True)

    # 测试3: 返回商品列表，点击"搜索"
    page.goto(f'{BASE_URL}/products')
    page.wait_for_load_state('networkidle')
    page.locator('input[placeholder*="商品名称"]').fill('Cat')
    page.locator('button:has-text("搜索")').first.click()
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    page.screenshot(path='D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\product_search.png', full_page=True)
    print(f"搜索后URL: {page.url}")

    # 测试4: 切换 Tab
    page.locator('.tab-item:has-text("在售")').click()
    time.sleep(0.5)
    page.screenshot(path='D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\product_tab.png', full_page=True)
    print("Tab 切换完成")

    browser.close()
    print("\n按钮交互测试全部通过")
