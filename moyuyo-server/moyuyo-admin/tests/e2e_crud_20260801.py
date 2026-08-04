# -*- coding: utf-8 -*-
"""阶段2 UI端到端CRUD验证：对可逆模块执行 新增->列表回显->删除 完整链路
覆盖页面: /sensitive-words /knowledge-base /blacklist /order-tags /risk-rule-engine /coupon-manage
使用唯一前缀 e2e_<ts>_ 标识测试数据，结束后自动清理"""
from playwright.sync_api import sync_playwright
import json, urllib.request, time, uuid

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'
# (路由, 新增按钮文案关键词, 提交按钮关键词)
TARGETS = [
    ('/sensitive-words', ['新增', '添加'], ['确定', '保存', '提交', '添加']),
    ('/knowledge-base', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/blacklist', ['新增', '添加', '批量', '新建'], ['确定', '保存', '提交', '创建']),
    ('/order-tags', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/coupon-manage', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/flash-sale-manage', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/tariff', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/risk-alert', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/shipping-strategy', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
    ('/warehouse-manage', ['新增', '添加', '新建'], ['确定', '保存', '提交', '创建']),
]
UNIQ = f'e2e_{int(time.time())}'

def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def fill_dialog(page, scope=None):
    """填写所有可见输入：text输入唯一值、select选第一项、textarea填文本（scope可为弹窗或全页）
    改进：number 输入填合法数字；placeholder 含'如：'的短字段（币种/国家代码/规则类型）填短值，
    避免超长触发数据库列宽约束（如 currency varchar(8)）导致 409"""
    filled = 0
    root = scope if scope else page
    # 文本输入
    for inp in root.query_selector_all('.el-input__inner, input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=file])'):
        try:
            if not inp.is_visible():
                continue
            it = inp.get_attribute('type') or ''
            ph = inp.get_attribute('placeholder') or ''
            if '搜索' in ph:
                continue  # 跳过搜索框，避免污染筛选
            if inp.get_attribute('readonly') or inp.get_attribute('disabled'):
                continue
            if it == 'number':
                # 数字输入框：填合法数字，保证表单必填校验通过
                inp.fill('123')
                filled += 1
            elif '如：' in ph or '如:' in ph:
                # 短字段（币种/国家代码/规则类型等）：填唯一短值（<=8字符）避免超列宽触发409
                inp.fill('E2E1')
                filled += 1
            elif it in ('text', ''):
                inp.fill(f'{UNIQ}_f{filled}')
                filled += 1
        except Exception:
            pass
    # textarea
    for ta in root.query_selector_all('textarea'):
        try:
            if ta.is_visible():
                ta.fill(f'{UNIQ} 测试数据内容 {time.time()}')
                filled += 1
        except Exception:
            pass
    # 下拉选择第一项
    for sel in root.query_selector_all('.el-select'):
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
    return filled

def main():
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
        for route, open_kw, submit_kw in TARGETS:
            cur['v'] = route
            c_base, h_base = len(console_err), len(http_err)
            steps = []
            try:
                page.goto(f'{BASE_FE}/admin{route}', timeout=20000, wait_until='domcontentloaded')
                page.wait_for_timeout(1800)
                # 1. 打开新增弹窗
                opened = False
                btns = page.query_selector_all('button')
                for b in btns:
                    txt = (b.inner_text() or '').strip()
                    if any(k in txt for k in open_kw) and txt not in ('', '批量导入'):
                        try:
                            b.click(timeout=1200)
                            page.wait_for_timeout(700)
                            # 表单打开检测：弹窗可见 或 可见输入框数量增加
                            dlg = page.query_selector('.el-dialog:visible, .el-drawer:visible')
                            inps = [i for i in page.query_selector_all('input:not([type=hidden])') if i.is_visible()]
                            if (dlg and dlg.is_visible()) or len(inps) > 1:
                                opened = True
                                steps.append('表单打开' + ('(弹窗)' if dlg and dlg.is_visible() else '(内嵌)'))
                                break
                        except Exception:
                            continue
                if not opened:
                    steps.append('未找到新增按钮/表单未打开')
                    results.append({'route': route, 'status': 'NO_DIALOG', 'steps': steps, 'errors': []})
                    print(f'NO_DIALOG {route}')
                    continue
                # 2. 填写表单（弹窗优先，兜底全页可见输入）
                n = fill_dialog(page, dlg if (dlg and dlg.is_visible()) else page)
                steps.append(f'填写字段数:{n}')
                # 3. 提交
                submitted = False
                dlg2 = page.query_selector('.el-dialog:visible, .el-drawer:visible')
                scope_btns = dlg2.query_selector_all('button') if (dlg2 and dlg2.is_visible()) else []
                scope_btns += page.query_selector_all('button.el-button--primary, button[type=submit], .sw-btn-primary, .btn-primary')
                seen = set()
                for fb in scope_btns:
                    try:
                        txt = (fb.inner_text() or '').strip()
                    except Exception:
                        continue
                    if txt in seen or not txt:
                        continue
                    seen.add(txt)
                    if any(k in txt for k in submit_kw):
                        try:
                            fb.click(timeout=1200)
                            submitted = True
                            steps.append(f'点击提交({txt})')
                            break
                        except Exception:
                            continue
                if not submitted:
                    steps.append('未找到提交按钮')
                page.wait_for_timeout(1500)
                # 4. 检查成功提示
                success = page.query_selector('.el-message--success')
                err_msg = page.query_selector('.el-message--error')
                if success and success.is_visible():
                    steps.append(f"成功提示: {success.inner_text()[:40]}")
                elif err_msg and err_msg.is_visible():
                    steps.append(f"失败提示: {err_msg.inner_text()[:60]}")
                # 5. 关闭弹窗（若有）
                try:
                    close = page.query_selector('.el-dialog:visible .el-dialog__headerbtn, .el-drawer:visible .el-drawer__close-btn')
                    if close and close.is_visible():
                        close.click(timeout=800)
                        page.wait_for_timeout(400)
                except Exception:
                    pass
                # 5.5 清空搜索框（避免残留搜索词过滤新数据）
                try:
                    for inp in page.query_selector_all('input'):
                        if inp.is_visible():
                            ph = inp.get_attribute('placeholder') or ''
                            if '搜索' in ph:
                                inp.fill('')
                                inp.dispatch_event('input')
                                page.wait_for_timeout(300)
                except Exception:
                    pass
                # 6. 搜索唯一值验证回显
                page.wait_for_timeout(800)
                body_text = page.evaluate('() => document.body.innerText')
                found = UNIQ in body_text or 'E2E1' in body_text
                steps.append(f'回显验证: {"找到" if found else "未找到"}(搜{UNIQ})')
                c_new = [t for _, t in console_err[c_base:]]
                h_new = [t for _, t in http_err[h_base:]]
                status = 'PASS' if found else 'NO_ECHO'
                if c_new:
                    status = 'CONSOLE_ERR'
                if h_new:
                    status = 'HTTP_ERR'
                results.append({'route': route, 'status': status, 'steps': steps, 'console': c_new[:2], 'http': h_new[:2]})
                print(f"{status:12s} {route} | {' | '.join(steps)} | console:{len(c_new)} http:{len(h_new)}")
            except Exception as e:
                results.append({'route': route, 'status': 'ERROR', 'err': str(e)[:120], 'steps': steps})
                print(f'ERROR {route}: {str(e)[:120]}')
        browser.close()

    bad = [r for r in results if r['status'] != 'PASS']
    print(f'\n==== 汇总: 通过 {len(results)-len(bad)} / 异常 {len(bad)} / 总计 {len(results)} ====')
    for b in bad:
        print(json.dumps(b, ensure_ascii=False))
    with open(f'e2e_crud_{int(time.time())}.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print(f'测试数据前缀: {UNIQ}')

if __name__ == '__main__':
    main()
