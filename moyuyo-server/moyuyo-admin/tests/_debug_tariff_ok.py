# -*- coding: utf-8 -*-
"""用合法值验证 tariff UI 创建流程（模拟真实用户）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
UNIQ = 'ui_tariff_ok'

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
    page.on('response', lambda r: captured.append((r.status, r.url[-40:])) if '/api/' in r.url and 'tariff' in r.url else None)
    page.goto(f'{BASE_FE}/admin/tariff', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1200)
    page.get_by_role('button', name='新建税率').click(timeout=3000)
    page.wait_for_timeout(500)
    # 合法值填充
    page.get_by_placeholder('如：电子产品、服装').fill('UI测试品类')
    page.get_by_placeholder('如：JP、KR、US').fill('ZZ')
    # 税率 el-input-number
    rate_inp = page.query_selector('.el-dialog:visible .el-input-number input')
    if rate_inp:
        rate_inp.fill('8')
    # 币种
    page.get_by_placeholder('如：USD、CNY').fill('USD')
    page.wait_for_timeout(300)
    page.get_by_role('button', name='保存').click(timeout=2000)
    page.wait_for_timeout(2000)
    for mtype in ['success', 'error', 'warning']:
        m = page.query_selector(f'.el-message--{mtype}:visible')
        if m:
            print(f'MESSAGE({mtype}):', m.inner_text()[:80])
    body = page.evaluate('() => document.body.innerText')
    print('UNIQ in body:', 'UI测试品类' in body)
    print('api:', captured)
    b.close()
