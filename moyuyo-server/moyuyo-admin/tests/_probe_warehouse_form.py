# -*- coding: utf-8 -*-
"""用合法值验证 warehouse UI 创建流程（按 label 定位，模拟真实用户）"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

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
    page.on('response', lambda r: captured.append((r.status, r.url[-45:])) if '/api/' in r.url and 'warehouse' in r.url else None)
    page.goto(f'{BASE_FE}/admin/warehouse-manage', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    page.get_by_role('button', name='新建').first.click(timeout=3000)
    page.wait_for_timeout(600)
    # 打印弹窗内输入框
    dlg = page.query_selector('.el-dialog:visible')
    if dlg:
        for i, inp in enumerate(dlg.query_selector_all('input')):
            try:
                print(f'input[{i}] ph={inp.get_attribute("placeholder")} editable={inp.is_editable()}')
            except Exception:
                pass
    # 使用 placeholder 定位（仓库表单）
    placeholders = page.query_selector_all('.el-dialog:visible input')
    texts = []
    for inp in placeholders:
        try:
            ph = inp.get_attribute('placeholder')
            if ph and '搜索' not in ph:
                texts.append(ph)
        except Exception:
            pass
    print('placeholders:', texts)
    b.close()
