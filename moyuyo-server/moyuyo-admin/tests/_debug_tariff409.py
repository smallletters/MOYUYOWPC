# -*- coding: utf-8 -*-
"""调试 tariff 409：捕获 configs/create 的完整请求/响应，以及页面列表实际返回"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
UNIQ = 'dbgtariff_1785538'

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

captured = []
with sync_playwright() as pw:
    token = login()
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

    def on_response(r):
        if '/api/' in r.url and ('tariff' in r.url):
            try:
                body = r.text()
                req_body = ''
                if r.request and r.request.post_data:
                    req_body = r.request.post_data[:300]
                captured.append((r.status, r.url[-60:], f'REQ:{req_body} | RESP:{body[:200]}'))
            except Exception:
                pass
    page.on('response', on_response)
    page.on('pageerror', lambda e: captured.append(('PAGEERR', '', str(e)[:150])))
    page.goto(f'{BASE_FE}/admin/tariff', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    # 打开弹窗
    try:
        page.get_by_role('button', name='新建税率').click(timeout=3000)
        page.wait_for_timeout(500)
    except Exception as e:
        print('open btn ERR:', e)
    # 填充
    for inp in page.query_selector_all('.el-dialog:visible .el-input__inner'):
        try:
            if inp.is_visible():
                inp.fill(f'{UNIQ}_x')
        except Exception:
            pass
    # 下拉选第一项
    for sel in page.query_selector_all('.el-dialog:visible .el-select'):
        try:
            if sel.is_visible():
                sel.click(timeout=800)
                page.wait_for_timeout(300)
                opt = page.query_selector('.el-select-dropdown:visible .el-select-dropdown__item')
                if opt:
                    opt.click(timeout=800)
                    page.wait_for_timeout(200)
        except Exception:
            pass
    # 保存
    try:
        page.get_by_role('button', name='保存').click(timeout=2000)
        page.wait_for_timeout(2000)
    except Exception as e:
        print('save ERR:', e)
    for mtype in ['success', 'error']:
        m = page.query_selector(f'.el-message--{mtype}:visible')
        if m:
            print(f'MESSAGE({mtype}):', m.inner_text()[:100])
    print('--- captured api calls ---')
    for c in captured:
        print(c)
    b.close()
