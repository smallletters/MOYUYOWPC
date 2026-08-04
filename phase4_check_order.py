"""获取真实订单详情数据"""
import json
import time
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173"
ORDER_ID = 186000010

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()

    # 登录
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

    # 获取订单详情原始数据
    result = page.evaluate(f"""async () => {{
        const res = await fetch('/api/admin/orders/{ORDER_ID}', {{
            headers: {{ 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }}
        }});
        const body = await res.json();
        return body;
    }}""")
    print(json.dumps(result, ensure_ascii=False, indent=2)[:3000])

    browser.close()
