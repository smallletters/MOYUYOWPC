#!/usr/bin/env python3
"""详细诊断 CustomerService 页面"""
import json
import time
from pathlib import Path
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

    requests = []
    responses = []
    errors = []
    console_msgs = []

    page.on("request", lambda r: requests.append({"url": r.url, "method": r.method}))
    page.on("response", lambda r: responses.append({"url": r.url, "status": r.status, "method": r.request.method}))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("console", lambda m: console_msgs.append({"type": m.type, "text": m.text}))

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

    # 访问 CustomerService
    print("\n访问 /admin/cs ...")
    page.goto(f"{BASE}/admin/cs", wait_until="domcontentloaded", timeout=30000)
    try:
        page.wait_for_load_state("networkidle", timeout=15000)
    except Exception as e:
        print(f"networkidle 超时: {e}")
    time.sleep(3)

    # 截图
    page.screenshot(path=r"D:\MOYUYOWPC\phase3_screenshots\CustomerService_debug.png", full_page=True)
    print(f"截图已保存")

    # 输出错误和失败请求
    print("\n--- Console 消息 (错误) ---")
    for m in console_msgs:
        if m["type"] in ("error", "warning"):
            print(f"  [{m['type']}] {m['text'][:200]}")

    print("\n--- 页面错误 (pageerror) ---")
    for e in errors:
        print(f"  {e[:300]}")

    print("\n--- 失败请求 (status >= 400) ---")
    for r in responses:
        if r["status"] >= 400 and "/api/" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-80:]}")

    print("\n--- 所有 API 请求 ---")
    for r in responses:
        if "/api/" in r["url"]:
            print(f"  [{r['status']}] {r['method']} {r['url'][-80:]}")

    print(f"\n总计: {len(requests)} 请求, {len(responses)} 响应, {len(errors)} 错误, {len(console_msgs)} console")

    browser.close()
