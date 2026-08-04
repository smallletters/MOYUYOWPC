# -*- coding: utf-8 -*-
"""调试 flash-sale 页面表单：检查 el-input-number 是否正常填充、保存后行为"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
UNIQ = 'dbgfs_1785537'

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

with sync_playwright() as pw:
    token = login()
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
    page.on('pageerror', lambda e: errors.append(f'PAGEERROR: {e}'))
    page.goto(f'{BASE_FE}/admin/flash-sale-manage', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1500)
    # 打开新建弹窗
    page.get_by_role('button', name='新建秒杀').click(timeout=3000)
    page.wait_for_timeout(600)
    dlg = page.query_selector('.el-dialog:visible')
    print('dialog visible:', bool(dlg))
    if dlg:
        # 列出弹窗内所有可见输入
        for i, inp in enumerate(dlg.query_selector_all('input')):
            try:
                vis = inp.is_visible()
                print(f'  input[{i}] type={inp.get_attribute("type")} class={inp.get_attribute("class")} visible={vis} readonly={inp.get_attribute("readonly")} ph={inp.get_attribute("placeholder")}')
            except Exception:
                pass
        # 尝试填充
        try:
            page.get_by_placeholder('秒杀活动名称').fill(f'{UNIQ}_name')
            print('name filled')
        except Exception as e:
            print('name fill ERR:', e)
        # el-input-number: 商品ID
        try:
            inputs = dlg.query_selector_all('.el-input-number input')
            print('input-number count:', len(inputs))
            if inputs:
                inputs[0].fill('183000001')
                page.wait_for_timeout(300)
                # 检查实际值
                v = inputs[0].input_value()
                print('productId value after fill:', v)
        except Exception as e:
            print('input-number ERR:', e)
        page.wait_for_timeout(300)
        # 点击保存
        try:
            page.get_by_role('button', name='保存', exact=True).click(timeout=2000)
            page.wait_for_timeout(1500)
        except Exception as e:
            print('save ERR:', e)
        # 检查提示
        for mtype in ['success', 'error', 'warning']:
            m = page.query_selector(f'.el-message--{mtype}:visible')
            if m:
                print(f'MESSAGE({mtype}):', m.inner_text()[:80])
        # 弹窗是否仍打开
        dlg2 = page.query_selector('.el-dialog:visible')
        print('dialog still visible:', bool(dlg2))
        body = page.evaluate('() => document.body.innerText')
        print('UNIQ in body:', UNIQ in body)
    b.close()
