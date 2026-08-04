# -*- coding: utf-8 -*-
"""深度交互测试：遍历所有页面，点击"操作类"按钮打开弹窗，验证弹窗可见/关闭，捕获console/HTTP错误。
只做只读+取消类操作，不做真实写入。"""
from playwright.sync_api import sync_playwright
import json, urllib.request, sys

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
# 弹窗触发关键词（按钮文案含这些词才点击，避免误触危险操作）
OPEN_KW = ['新增', '添加', '创建', '新建', '编辑', '设置', '配置', '查看', '详情', '导出', '导入',
           '模板', '筛选', '条件', '说明', '规则', '策略', '模板下载', '下载', '帮助', '预览']
DANGER_KW = ['删除', '移除', '封禁', '禁用', '下线', '重置', '清空', '退款', '拒绝', '强制']

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def main():
    routes = json.load(open('../smoke_20260801.json', encoding='utf-8'))
    routes = [r['route'] for r in routes]
    token = login()
    results = []
    cur = {'v': ''}
    console_err, http_err = [], []

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
        for route in routes:
            cur['v'] = route
            c_base, h_base = len(console_err), len(http_err)
            opened_dialogs = 0
            errors = []
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1500)
                # 尝试点击操作类按钮打开弹窗
                btns = page.query_selector_all('button, .el-button')
                for b in btns[:60]:
                    try:
                        txt = (b.inner_text() or '').strip()
                        if not txt or len(txt) > 12:
                            continue
                        if not any(k in txt for k in OPEN_KW):
                            continue
                        if any(k in txt for k in DANGER_KW):
                            continue
                        b.click(timeout=1500)
                        page.wait_for_timeout(450)
                        # 检查是否有可见弹窗
                        dlgs = page.query_selector_all('.el-dialog:visible, .el-drawer:visible, [role="dialog"]')
                        visible = [d for d in dlgs if d.is_visible() and d.bounding_box()['width'] > 100]
                        if visible:
                            opened_dialogs += 1
                            # 点击取消/关闭按钮
                            try:
                                cancel = page.query_selector('.el-dialog__headerbtn, .el-dialog:visible .el-button--info, .el-dialog:visible button:has-text("取 消"), .el-dialog:visible button:has-text("取消"), .el-drawer__close-btn')
                                if cancel and cancel.is_visible():
                                    cancel.click(timeout=1200)
                                    page.wait_for_timeout(300)
                            except Exception:
                                pass
                    except Exception:
                        pass
                # 收集新错误
                c_new = [t for _, t in console_err[c_base:]]
                h_new = [t for _, t in http_err[h_base:]]
                status = 'OK'
                if c_new:
                    status = 'CONSOLE_ERR'; errors += c_new[:3]
                if h_new:
                    status = 'HTTP_ERR'; errors += h_new[:3]
                results.append({'route': route, 'status': status, 'dialogs_opened': opened_dialogs, 'errors': errors})
                print(f"{status:12s} {route}  弹窗打开:{opened_dialogs}  console:{len(c_new)} http:{len(h_new)}")
            except Exception as e:
                results.append({'route': route, 'status': 'ERROR', 'err': str(e)[:120]})
                print(f'ERROR {route}: {str(e)[:120]}')
        browser.close()

    bad = [r for r in results if r['status'] != 'OK']
    print(f'\n==== 汇总: 正常 {len(results)-len(bad)} / 异常 {len(bad)} / 总计 {len(results)} ====')
    for b in bad:
        print(json.dumps(b, ensure_ascii=False))
    with open('deep_interact_20260801.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=1)

if __name__ == '__main__':
    main()
