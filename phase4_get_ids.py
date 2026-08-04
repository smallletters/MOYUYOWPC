"""通过管理后台 API 获取真实订单和商品 ID"""
import json
import time
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173"
TOKEN = None

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        args=["--no-sandbox", "--disable-dev-shm-usage"],
    )
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()

    # 登录拿 token
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

    # 从 localStorage 读 token
    token = page.evaluate("() => localStorage.getItem('admin_token')")
    print(f"Token: {token[:30] if token else 'None'}...")

    # 通过 fetch 调用 API
    order_ids = page.evaluate("""async () => {
        const res = await fetch('/api/admin/orders/list?page=1&size=5', {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }
        });
        const body = await res.json();
        if (body && body.data && body.data.list) {
            return body.data.list.map(o => ({ id: o.id, orderNo: o.orderNo, status: o.status }));
        }
        return null;
    }""")
    print(f"\n订单 ID 列表: {order_ids}")

    product_ids = page.evaluate("""async () => {
        const res = await fetch('/api/admin/products/list?page=1&size=5', {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('admin_token') }
        });
        const body = await res.json();
        const data = body && body.data;
        if (data) {
            const records = data.records || data.list || data;
            if (Array.isArray(records)) {
                return records.map(p => ({ id: p.id, name: p.name, onSale: p.onSale }));
            }
        }
        return null;
    }""")
    print(f"\n商品 ID 列表: {product_ids}")

    browser.close()
