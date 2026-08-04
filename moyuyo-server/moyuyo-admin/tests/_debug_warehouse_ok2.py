# -*- coding: utf-8 -*-
"""按 placeholder 精确填充 warehouse 表单并验证创建回显"""
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
    # 按 placeholder 填充（限定弹窗作用域）
    dlg = page.locator('.el-dialog:visible')
    dlg.get_by_placeholder('请输入仓库名称').fill('UI仓库验证')
    dlg.get_by_placeholder('请输入所在城市').fill('上海')
    dlg.get_by_placeholder('请输入管理员姓名').fill('测试员')
    dlg.get_by_placeholder('请输入联系电话').fill('13800138000')
    # 仓库类型：选择第一个可见下拉选项
    try:
        sel = page.locator('.el-dialog:visible .el-select').first
        if sel.count() > 0:
            sel.click(timeout=800)
            page.wait_for_timeout(300)
            opt = page.query_selector('.el-select-dropdown:visible .el-select-dropdown__item')
            if opt:
                opt.click(timeout=800)
                page.wait_for_timeout(200)
    except Exception:
        pass
    page.wait_for_timeout(300)
    page.get_by_role('button', name='保存').click(timeout=2000)
    page.wait_for_timeout(2500)
    for mtype in ['success', 'error', 'warning']:
        m = page.query_selector(f'.el-message--{mtype}:visible')
        if m:
            print(f'MESSAGE({mtype}):', m.inner_text()[:80])
    body = page.evaluate('() => document.body.innerText')
    print('UI仓库验证 in body:', 'UI仓库验证' in body)
    print('api:', captured)
    b.close()
