# -*- coding: utf-8 -*-
"""验证已改造页面渲染 + 截图（AppVersion/UserProfile/ComplaintManage/OrderIntercept/ProductReport/SensitiveWords）"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import os

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
ROUTES = ['/app-version', '/user-profile', '/complaint', '/order-intercept', '/product-report', '/sensitive-words']
SHOT_DIR = 'tests/screenshots_20260801'


def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']


def main():
    os.makedirs(SHOT_DIR, exist_ok=True)
    token = login()
    cur = {'v': ''}
    console_err = []
    http_err = []

    def on_console(m):
        if m.type == 'error':
            console_err.append((cur['v'], m.text))

    def on_pageerror(e):
        console_err.append((cur['v'], f'PAGEERROR: {e}'))

    def on_response(r):
        if r.status >= 400 and '/api/' in r.url:
            http_err.append((cur['v'], f'{r.status} {r.url}'))

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        page.on('console', on_console)
        page.on('pageerror', on_pageerror)
        page.on('response', on_response)
        for route in ROUTES:
            cur['v'] = route
            base = len(console_err)
            page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
            page.wait_for_timeout(2200)
            body_len = page.evaluate('() => document.body.innerText.length')
            overflow = page.evaluate('() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2')
            new_console = console_err[base:]
            shot_name = route.strip('/').replace('/', '_') + '.png'
            page.screenshot(path=os.path.join(SHOT_DIR, shot_name), full_page=False)
            print(f"{route}: body={body_len} overflow={overflow} console_err={len(new_console)} shot={shot_name}")
            for _, t in new_console[:3]:
                print(f'   console: {t[:150]}')
        browser.close()
        print(f'截图目录: {SHOT_DIR}')


if __name__ == '__main__':
    main()
