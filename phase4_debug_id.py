"""检查 phase3 动态 ID 解析的调试脚本"""
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

    # 检查 token
    token = page.evaluate("() => localStorage.getItem('admin_token')")
    print(f"Token: {token[:30] if token else 'None'}")

    # 模拟 evaluate 调用
    first_id = page.evaluate("""async (args) => {
        const [apiUrl, idField] = args;
        console.log('Fetching:', apiUrl, 'Field:', idField);
        const token = localStorage.getItem('admin_token');
        console.log('Token available:', !!token);
        const res = await fetch(apiUrl, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        console.log('Response status:', res.status);
        const body = await res.json();
        console.log('Body keys:', Object.keys(body || {}));
        const data = body && body.data;
        if (data) {
            const records = data.records || data.list || data;
            console.log('Records type:', Array.isArray(records) ? 'array' : typeof records);
            console.log('Records length:', Array.isArray(records) ? records.length : 'N/A');
            if (Array.isArray(records) && records.length > 0) {
                return records[0][idField];
            }
        }
        return null;
    }""", ["/api/admin/orders/list", "id"])
    print(f"First order ID: {first_id}")

    browser.close()
