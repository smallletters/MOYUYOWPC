# -*- coding: utf-8 -*-
"""用合法值验证 warehouse UI 创建流程（模拟真实用户）"""
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
    # 打开新建弹窗
    page.get_by_role('button', name='新建').first.click(timeout=3000)
    page.wait_for_timeout(500)
    # 填充表单字段
    fields = {
        '仓库名称': 'UI仓库验证',
        '所在城市': '上海',
        '仓库管理员': '测试员',
        '联系电话': '13800138000',
    }
    dlg = page.query_selector('.el-dialog:visible')
    if dlg:
        inps = dlg.query_selector_all('input')
        # 逐个填：第一个=名称，第二个=城市，第三个=管理员，第四个=电话
        if len(inps) >= 1: inps[0].fill('UI仓库验证')
        if len(inps) >= 2: inps[1].fill('上海')
        if len(inps) >= 3: inps[2].fill('测试员')
        if len(inps) >= 4: inps[3].fill('13800138000')
        # 仓库类型 select
        try:
            sel = dlg.query_selector('.el-select')
            if sel:
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
    page.wait_for_timeout(2000)
    for mtype in ['success', 'error', 'warning']:
        m = page.query_selector(f'.el-message--{mtype}:visible')
        if m:
            print(f'MESSAGE({mtype}):', m.inner_text()[:80])
    body = page.evaluate('() => document.body.innerText')
    print('UI仓库验证 in body:', 'UI仓库验证' in body)
    print('api:', captured)
    b.close()
