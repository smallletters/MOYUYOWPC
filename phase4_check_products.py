"""检查不同商品 ID 在详情 API 的可用性"""
import json
import time
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173"

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()

    page.goto(f"{BASE}/admin/login", wait_until="domcontentloaded", timeout=30000)
    page.wait_for_load_state("networkidle", timeout=15000)
    try:
        page.fill('input[type="email"], input[placeholder*="邮"]', "admin@moyuyo.com", timeout=5000)
        page.fill('input[type="password"]', "123456", timeout=5000)
        page.click('button:has-text("登录"), button[type="submit"]', timeout=5000)
        page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
    except Exception as e:
        print(f"登录失败: {e}")

    # 测试多个商品 ID 的详情 API
    for product_id in [2080199390045450200, 183000009, 183000006, 183000005, 183000004]:
        result = page.evaluate(f"""async () => {{
            const res = await fetch('/api/admin/products/{product_id}', {{
                headers: {{ 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }}
            }});
            const body = await res.json();
            return {{
                code: body.code,
                message: body.message,
                hasData: !!(body.data),
                dataName: body.data ? body.data.name : null
            }};
        }}""")
        print(f"ID={product_id}: {result}")

    browser.close()
