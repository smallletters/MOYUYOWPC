# -*- coding: utf-8 -*-
"""客服绩效看板新页面验证"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    consoles = []
    https = []

    def on_console(m):
        if m.type == 'error':
            consoles.append(m.text)

    def on_response(r):
        if r.status >= 400 and '/api/' in r.url:
            https.append(f'{r.status} {r.url}')

    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        p.on('console', on_console)
        p.on('response', on_response)
        p.goto('http://localhost:5173/admin/cs-performance', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(2000)
        body = p.evaluate('() => document.body.innerText')
        ovf = p.evaluate('() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2')
        print('URL:', p.url)
        print('BODY_LEN:', len(body))
        print('OVERFLOW:', ovf)
        print('HAS_HEADER:', '客服绩效看板' in body)
        print('HAS_KPI:', '团队整体 KPI' in body)
        print('HAS_RANK:', '客服排名' in body)
        print('HAS_CHART:', '今日工单处理量' in body)
        print('HAS_ALERT:', '低分预警' in body)
        print('HAS_AGENT:', '小张' in body)
        print('CONSOLE:', consoles[:5])
        print('HTTP_ERR:', https[:5])
        p.screenshot(path='cs_performance_new.png', full_page=True)
        b.close()


if __name__ == '__main__':
    main()
