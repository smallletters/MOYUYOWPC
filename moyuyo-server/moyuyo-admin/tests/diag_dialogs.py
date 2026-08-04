# -*- coding: utf-8 -*-
"""诊断 BlacklistManage / KnowledgeBase / OrderTags 页面：按钮是否存在、点击后弹窗是否打开"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
ROUTES = ['/blacklist', '/knowledge-base', '/order-tags']

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

with sync_playwright() as pw:
    token = login()
    browser = pw.chromium.launch()
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
    for route in ROUTES:
        print(f'\n===== {route} =====')
        page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        body = page.evaluate('() => document.body.innerText').strip()[:200]
        print(f'页面文本: {body}')
        btns = page.query_selector_all('button')
        print(f'按钮数: {len(btns)}')
        for b in btns[:10]:
            try:
                print(f'  btn: "{b.inner_text().strip()}" visible={b.is_visible()}')
            except Exception as e:
                print(f'  btn err: {e}')
        # 点击第一个主按钮
        for b in btns:
            txt = (b.inner_text() or '').strip()
            if '新建' in txt or '创建' in txt:
                print(f'点击: {txt}')
                b.click(timeout=1500)
                page.wait_for_timeout(900)
                dlg = page.query_selector('.el-dialog')
                if dlg:
                    print(f'el-dialog 存在, visible={dlg.is_visible()}, title={dlg.query_selector(".el-dialog__title").inner_text() if dlg.query_selector(".el-dialog__title") else "?"}')
                else:
                    print('el-dialog 不存在')
                break
    browser.close()
