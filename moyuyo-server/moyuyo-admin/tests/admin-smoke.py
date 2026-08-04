from playwright.sync_api import sync_playwright
import time

BASE_URL = 'http://localhost:8080/admin'

def test_page(page, path, name):
    """测试单个页面是否能正常加载并包含主要按钮"""
    page.goto(f'{BASE_URL}{path}')
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    screenshot_path = f'D:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\{name.lower().replace(" ", "_")}.png'
    page.screenshot(path=screenshot_path, full_page=True)
    buttons = page.locator('button').all()
    print(f"[{name}] URL={page.url}, 标题={page.title()}, 按钮数={len(buttons)}")
    for btn in buttons[:5]:
        text = btn.inner_text().strip()
        if text:
            print(f"  - {text}")
    return len(buttons) > 0

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
    )
    page = browser.new_page(viewport={'width': 1920, 'height': 1080})

    # 1. 登录
    page.goto(f'{BASE_URL}/')
    page.wait_for_load_state('networkidle')
    page.locator('input[type="email"]').fill('admin@moyuyo.com')
    page.locator('input[type="password"]').fill('123456')
    page.locator('button.login-btn').click()
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    print(f"登录成功: {page.url}")

    # 2. 检查侧边栏导航项
    nav_items = page.locator('.nav-item').all()
    print(f"侧边栏导航项数量: {len(nav_items)}")
    for item in nav_items[:12]:
        text = item.inner_text().strip()
        if text:
            print(f"  导航: {text}")

    # 3. 测试关键页面
    pages = [
        ('/dashboard', '仪表盘'),
        ('/products', '商品管理'),
        ('/orders', '订单管理'),
        ('/users', '用户管理'),
        ('/marketing', '营销管理'),
        ('/reviews', '内容审核'),
        ('/finance', '财务概览'),
        ('/logistics', '物流管理'),
    ]
    results = []
    for path, name in pages:
        try:
            ok = test_page(page, path, name)
            results.append((name, ok))
        except Exception as e:
            print(f"[{name}] 测试失败: {e}")
            results.append((name, False))

    print("\n页面验证结果:")
    for name, ok in results:
        print(f"  {name}: {'通过' if ok else '失败'}")

    browser.close()
