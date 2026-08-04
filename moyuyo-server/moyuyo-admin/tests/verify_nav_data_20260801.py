# -*- coding: utf-8 -*-
"""阶段4：页面间数据传递链路验证（列表→详情、query 参数）"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")

        # 1) 订单列表 → 详情（点击"详情"链接跳转 /orders/:id）
        print('===== 订单列表 → 详情 =====')
        p.goto('http://localhost:5173/admin/orders', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1800)
        detail_links = p.locator('button:has-text("详情"), .table-link:has-text("详情")').all()
        if detail_links:
            detail_links[0].click(timeout=5000)
            p.wait_for_timeout(1800)
            print('  URL:', p.url)
            body = p.evaluate('() => document.body.innerText')
            print('  详情页有订单信息:', '订单' in body and len(body) > 100)
            # 返回
            p.go_back()
            p.wait_for_timeout(1000)
            print('  返回列表 OK:', '/admin/orders' in p.url)

        # 2) 用户画像：query 传参搜索
        print('===== 用户画像 query 传参 =====')
        p.goto('http://localhost:5173/admin/user-profile', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1200)
        # 输入框填充用户ID
        inputs = p.locator('input').all()
        if inputs:
            inputs[0].fill('1')
            btn = p.locator('button:has-text("查询"), button:has-text("搜索")').first
            if btn.count() > 0:
                btn.click(timeout=3000)
                p.wait_for_timeout(1500)
            body = p.evaluate('() => document.body.innerText')
            print('  画像有数据:', len(body) > 100)
            p.screenshot(path='phase4_user_profile.png')

        # 3) 数据传递：工单处理跳转 /ticket?query
        print('===== 工单处理 query 跳转 =====')
        p.goto('http://localhost:5173/admin/cs', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1500)
        process_links = p.locator('span.table-link:has-text("处理")').all()
        if process_links:
            process_links[0].click(timeout=5000)
            p.wait_for_timeout(1500)
            print('  URL:', p.url)
            has_query = 'id=' in p.url and 'action' in p.url
            print('  query 参数传递:', has_query)
        b.close()


if __name__ == '__main__':
    main()
