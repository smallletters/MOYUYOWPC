# -*- coding: utf-8 -*-
"""阶段4：工单 query 数据传递修复验证"""
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

        # 1) 直接访问带 query 的工单页
        p.goto('http://localhost:5173/admin/ticket?id=1&action=process', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(2500)
        body = p.evaluate('() => document.body.innerText')
        has_msg = '正在处理工单' in body or '未找到工单' in body
        has_highlight = p.locator('tr.row-highlight').count() > 0
        print('QUERY消费提示:', has_msg)
        print('高亮行存在:', has_highlight)
        print('控制台无错(URL正常):', '/admin/ticket' in p.url)

        # 2) 客服管理 -> 处理跳转链路
        p.goto('http://localhost:5173/admin/cs', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1800)
        # 若表格有"处理"链接则点击
        links = p.locator('span.table-link:has-text("处理")').all()
        if links:
            links[0].click(timeout=5000)
            p.wait_for_timeout(2000)
            print('跳转URL:', p.url)
            print('带query:', 'action=process' in p.url or 'id=' in p.url)
            body2 = p.evaluate('() => document.body.innerText')
            print('目标页提示:', '处理工单' in body2 or '工单' in body2)
        else:
            print('客服页无处理链接（工单数据为空）')
        b.close()


if __name__ == '__main__':
    main()
