"""诊断脚本：检查单个页面的实际DOM结构"""
import time
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080/admin"
LOGIN_EMAIL = "admin@moyuyo.com"
LOGIN_PASSWORD = "123456"

with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path=r"C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe"
    )
    context = browser.new_context(viewport={"width": 1920, "height": 1080})
    page = context.new_page()

    # 收集请求/响应
    network = []
    def on_response(response):
        if '/api/' in response.url:
            network.append(f"{response.status} {response.request.method} {response.url}")

    page.on("response", on_response)

    # 登录
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state("networkidle")
    page.locator('input[type="email"]').fill(LOGIN_EMAIL)
    page.locator('input[type="password"]').fill(LOGIN_PASSWORD)
    page.locator('button.login-btn').click()
    page.wait_for_url("**/dashboard", timeout=10000)
    print(f"[LOGIN] OK\n")

    # 展开所有 nav-group
    for header in page.locator('.nav-group-header').all():
        try:
            header.click()
            time.sleep(0.1)
        except Exception:
            pass
    time.sleep(0.5)

    # 访问商品报表页面
    print("访问 /product-report")
    page.goto(f"{BASE_URL}/product-report", wait_until="domcontentloaded", timeout=15000)
    page.wait_for_load_state("networkidle", timeout=10000)
    time.sleep(3)

    # 截图
    page.screenshot(path="D:/MOYUYOWPC/moyuyo-server/moyuyo-admin/tests/diagnose_product_report.png", full_page=True)
    print(f"截图已保存: diagnose_product_report.png")

    # 打印页面URL
    print(f"当前URL: {page.url}")

    # 打印所有按钮
    buttons = page.locator("button").all()
    print(f"\n总按钮数: {len(buttons)}")
    for i, btn in enumerate(buttons[:20]):
        try:
            text = btn.inner_text().strip()
            visible = btn.is_visible()
            classes = btn.get_attribute("class") or ""
            print(f"  [{i}] visible={visible} class='{classes[:60]}' text='{text[:40]}'")
        except Exception as e:
            print(f"  [{i}] error: {e}")

    # 打印所有 el-button
    el_buttons = page.locator(".el-button").all()
    print(f"\nel-button 总数: {len(el_buttons)}")
    for i, btn in enumerate(el_buttons[:20]):
        try:
            text = btn.inner_text().strip()
            visible = btn.is_visible()
            print(f"  [{i}] visible={visible} text='{text[:40]}'")
        except Exception as e:
            print(f"  [{i}] error: {e}")

    # 打印页面主要内容
    h2s = page.locator("h2").all()
    print(f"\nH2 标题:")
    for h in h2s:
        try:
            print(f"  {h.inner_text()}")
        except Exception:
            pass

    # 打印所有 el-form-item 标签
    form_items = page.locator(".el-form-item__label").all()
    print(f"\n表单标签:")
    for fi in form_items:
        try:
            print(f"  {fi.inner_text()}")
        except Exception:
            pass

    # 网络请求
    print(f"\n最近的网络请求 (前20):")
    for line in network[-20:]:
        print(f"  {line}")

    # 检查是否有404
    err_404 = [n for n in network if n.startswith("404")]
    err_500 = [n for n in network if n.startswith("5")]
    print(f"\n404错误: {len(err_404)} 个")
    for e in err_404[:5]:
        print(f"  {e}")
    print(f"5xx错误: {len(err_500)} 个")
    for e in err_500[:5]:
        print(f"  {e}")

    # 完整HTML片段
    body_html = page.evaluate("document.body.innerHTML.length")
    print(f"\nbody HTML 长度: {body_html}")
    page_text = page.evaluate("document.body.innerText")
    print(f"body 文本前500: {page_text[:500]}")

    browser.close()
