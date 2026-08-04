# -*- coding: utf-8 -*-
"""阶段4 - 批量截图（用于 UI 前后对比）"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/admin"
OUT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r"D:\MOYUYOWPC\ui_before")
OUT_DIR.mkdir(exist_ok=True)

# (文件名, 路由)
PAGES = [
    ("dashboard", "dashboard"),
    ("order_list", "orders"),
    ("product_list", "products"),
    ("user_list", "users"),
    ("marketing_list", "marketing"),
    ("coupon_manage", "coupon-manage"),
    ("cms_manage", "cms"),
    ("analytics", "analytics"),
    ("customer_service", "cs"),
    ("inventory", "inventory"),
]


def main():
    with sync_playwright() as p:
        b = p.chromium.launch()
        ctx = b.new_context(viewport={"width": 1600, "height": 900})
        pg = ctx.new_page()
        pg.goto(f"{BASE}/login", wait_until="networkidle")
        pg.wait_for_timeout(500)
        try:
            pg.fill("input[placeholder*='邮箱'], input[placeholder*='账号'], input[type='text']", "admin@moyuyo.com", timeout=4000)
        except Exception:
            pass
        pg.fill("input[type='password']", "123456", timeout=4000)
        pg.click("button[type='submit'], button:has-text('登录')", timeout=4000)
        pg.wait_for_timeout(1800)
        for name, route in PAGES:
            pg.goto(f"{BASE}/{route}", wait_until="networkidle")
            pg.wait_for_timeout(1800)
            shot = OUT_DIR / f"{name}.png"
            pg.screenshot(path=str(shot))
            print(f"saved: {shot}")
        b.close()


if __name__ == "__main__":
    main()
