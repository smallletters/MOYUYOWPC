# -*- coding: utf-8 -*-
"""用合法值验证 risk-alert UI 创建流程（模拟真实用户）"""
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
    page.on('response', lambda r: captured.append((r.status, r.url[-45:])) if '/api/' in r.url and 'risk-alert' in r.url else None)
    page.goto(f'{BASE_FE}/admin/risk-alert', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(1200)
    page.get_by_role('button', name='新建告警配置').click(timeout=3000)
    page.wait_for_timeout(500)
    # 合法值
    page.get_by_placeholder('告警名称').fill('UI告警测试')
    page.get_by_placeholder('如：order_failure_rate').fill('order_failure_rate')
    thr = page.query_selector('.el-dialog:visible .el-input-number input')
    if thr:
        thr.fill('5')
    page.get_by_placeholder('如：email,sms').fill('email')
    page.get_by_placeholder('用户ID，逗号分隔').fill('1,2')
    page.wait_for_timeout(300)
    page.get_by_role('button', name='保存').click(timeout=2000)
    page.wait_for_timeout(2000)
    for mtype in ['success', 'error', 'warning']:
        m = page.query_selector(f'.el-message--{mtype}:visible')
        if m:
            print(f'MESSAGE({mtype}):', m.inner_text()[:80])
    body = page.evaluate('() => document.body.innerText')
    print('UI告警测试 in body:', 'UI告警测试' in body)
    print('api:', captured)
    b.close()
