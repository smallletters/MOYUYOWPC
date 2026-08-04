# -*- coding: utf-8 -*-
"""布局审计：检测横向溢出、表格溢出、元素对齐问题"""
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/admin"
PAGES = ["dashboard", "orders", "products", "users", "marketing", "coupon-manage", "cms", "analytics", "cs", "inventory"]


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
            pg.wait_for_timeout(1800)
            audit = pg.evaluate("""() => {
                const doc = document.documentElement;
                const body = document.body;
                const issues = [];
                // 1. 横向溢出
                if (doc.scrollWidth > doc.clientWidth + 2) {
                    issues.push(`页面横向溢出: scrollWidth=${doc.scrollWidth} clientWidth=${doc.clientWidth}`);
                }
                // 2. 内容区宽度
                const content = document.querySelector('.admin-content, .content, main');
                if (content) {
                    const cr = content.getBoundingClientRect();
                    issues.push(`内容区: x=${Math.round(cr.x)} w=${Math.round(cr.width)}`);
                }
                // 3. 表格是否溢出容器
                document.querySelectorAll('.el-table, .data-table, table').forEach((t, i) => {
                    const tr = t.getBoundingClientRect();
                    if (tr.width > (t.parentElement ? t.parentElement.getBoundingClientRect().width + 2 : 0)) {
                        issues.push(`表格溢出容器: ${t.className.slice(0,40)} w=${Math.round(tr.width)}`);
                    }
                });
                // 4. 固定列错位 / 重叠元素
                document.querySelectorAll('.el-table__fixed, .el-table-fixed-column--left, .el-table-fixed-column--right').forEach(e => {
                    issues.push(`存在固定列: ${e.className.slice(0,50)}`);
                });
                // 5. KPI 卡片数量与宽度
                const kpis = document.querySelectorAll('.kpi-card');
                if (kpis.length) {
                    const w = kpis[0].getBoundingClientRect().width;
                    issues.push(`KPI卡片: ${kpis.length}张, 单卡宽=${Math.round(w)}`);
                }
                // 6. 按钮/输入框高度是否一致
                const inputs = document.querySelectorAll('.form-group input, .el-input__wrapper, input[class*=input]');
                const hset = new Set();
                inputs.forEach(i => { const r = i.getBoundingClientRect(); if (r.height > 0) hset.add(Math.round(r.height)); });
                if (hset.size > 1) issues.push(`输入控件高度不一致: ${[...hset].join('/')}`);
                return issues;
            }""")
            print(f"=== {route}")
            for line in audit:
                print(f"  {line}")
        b.close()


if __name__ == "__main__":
    main()
