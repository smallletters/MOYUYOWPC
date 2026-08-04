# -*- coding: utf-8 -*-
"""测量各页面内容起始偏移，对比间距一致性"""
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/admin"
PAGES = ["dashboard", "orders", "products", "users", "marketing", "coupon-manage", "cms", "analytics", "cs", "inventory", "finance", "refund", "rbac", "sms", "risk-control"]


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

        for route in PAGES:
            pg.goto(f"{BASE}/{route}", wait_until="networkidle")
            pg.wait_for_timeout(1500)
            m = pg.evaluate("""() => {
                // 找内容区第一个可见的顶层子元素
                const content = document.querySelector('.admin-content');
                if (!content) return null;
                const kids = [...content.children].filter(c => c.offsetParent !== null && c.offsetHeight > 10);
                if (!kids.length) return null;
                const el = kids[0];
                const r = el.getBoundingClientRect();
                const cs = getComputedStyle(el);
                return {
                    top: Math.round(r.top), left: Math.round(r.left),
                    padLeft: cs.paddingLeft, padTop: cs.paddingTop,
                    width: Math.round(r.width)
                };
            }""")
            print(f"{route:16s} 首元素 top={m['top'] if m else '?'} left={m['left'] if m else '?'} padL={m['padLeft'] if m else '?'} padT={m['padTop'] if m else '?'} w={m['width'] if m else '?'}")
        b.close()


if __name__ == "__main__":
    main()
