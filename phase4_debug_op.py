#!/usr/bin/env python3
"""诊断 OrderDetail 和 ProductEdit 页面"""
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

    console_msgs = []
    errors = []
    responses = []

    page.on("console", lambda m: console_msgs.append({"type": m.type, "text": m.text}))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("response", lambda r: responses.append({"url": r.url, "status": r.status, "method": r.request.method}))

    # 登录
    print("登录中...")
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

    # === OrderDetail ===
    print("\n=== 访问 /admin/orders/1 (OrderDetail) ===")
    page.goto(f"{BASE}/admin/orders/1", wait_until="domcontentloaded", timeout=30000)
    try:
        page.wait_for_load_state("networkidle", timeout=15000)
    except Exception as e:
        print(f"networkidle 超时: {e}")
    time.sleep(3)
    page.screenshot(path=r"D:\MOYUYOWPC\phase3_screenshots\OrderDetail_debug.png", full_page=True)

    print("\n--- OrderDetail Console (errors) ---")
    for m in console_msgs:
        if m["type"] in ("error", "warning"):
            text = m["text"]
            if "VUE_ROUTER" not in text and "favicon" not in text:
                print(f"  [{m['type']}] {text[:300]}")

    print("\n--- OrderDetail PageError ---")
    for e in errors:
        if "favicon" not in e:
            print(f"  {e[:300]}")

    print("\n--- OrderDetail 失败 API ---")
    for r in responses:
        if r["status"] >= 400 and "/api/" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-100:]}")

    print("\n--- OrderDetail 所有 API ---")
    for r in responses:
        if "/api/" in r["url"] and "/orders" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-100:]}")

    # 重置收集器
    console_msgs.clear()
    errors.clear()
    responses.clear()

    # === ProductEdit ===
    print("\n=== 访问 /admin/products/edit/1 (ProductEdit) ===")
    page.goto(f"{BASE}/admin/products/edit/1", wait_until="domcontentloaded", timeout=30000)
    try:
        page.wait_for_load_state("networkidle", timeout=15000)
    except Exception as e:
        print(f"networkidle 超时: {e}")
    time.sleep(3)
    page.screenshot(path=r"D:\MOYUYOWPC\phase3_screenshots\ProductEdit_debug.png", full_page=True)

    print("\n--- ProductEdit Console (errors) ---")
    for m in console_msgs:
        if m["type"] in ("error", "warning"):
            text = m["text"]
            if "VUE_ROUTER" not in text and "favicon" not in text:
                print(f"  [{m['type']}] {text[:300]}")

    print("\n--- ProductEdit PageError ---")
    for e in errors:
        if "favicon" not in e:
            print(f"  {e[:300]}")

    print("\n--- ProductEdit 失败 API ---")
    for r in responses:
        if r["status"] >= 400 and "/api/" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-100:]}")

    print("\n--- ProductEdit 所有 API ---")
    for r in responses:
        if "/api/" in r["url"] and "/products" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-100:]}")

    browser.close()
