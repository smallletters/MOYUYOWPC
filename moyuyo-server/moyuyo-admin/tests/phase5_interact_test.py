"""
阶段2 交互深度测试（核心页面）
对每个核心页面执行按钮点击/弹窗开合/表单提交链路验证：
1. 页面加载无 console 错误
2. 点击"新增/创建/搜索/重置"等按钮，验证弹窗出现或接口调用正常
3. 弹窗内确认/取消按钮行为
4. 行内操作按钮（编辑/查看/详情）跳转或弹窗正常
"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

def get_token():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def check_page(page, path, actions, name):
    """执行页面交互动作序列"""
    console_errors = []
    http_failures = []

    def on_console(msg):
        if msg.type == 'error':
            console_errors.append(msg.text)
    def on_pageerror(exc):
        console_errors.append(f'PAGEERROR: {exc}')
    def on_response(resp):
        if resp.status >= 400:
            http_failures.append(f'{resp.status} {resp.url}')
    try:
        page.remove_listener('console', on_console)
        page.remove_listener('pageerror', on_pageerror)
        page.remove_listener('response', on_response)
    except Exception:
        pass
    page.on('console', on_console)
    page.on('pageerror', on_pageerror)
    page.on('response', on_response)

    try:
        page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
    except Exception as e:
        print(f'[LOAD_FAIL] {path} ({name}): {e}')
        return

    problems = []
    for act in actions:
        act_name = act.get('name', '')
        try:
            if act['type'] == 'click_text':
                page.get_by_text(act['text'], exact=act.get('exact', False)).first.click(timeout=5000)
                page.wait_for_timeout(act.get('wait', 1200))
            elif act['type'] == 'click_selector':
                page.locator(act['selector']).first.click(timeout=5000)
                page.wait_for_timeout(act.get('wait', 1200))
            elif act['type'] == 'fill':
                page.locator(act['selector']).fill(act['value'])
            elif act['type'] == 'close_modal':
                # 关闭弹窗：优先点取消/关闭按钮，否则按 Esc
                try:
                    page.get_by_text('取消', exact=True).first.click(timeout=2000)
                except Exception:
                    try:
                        page.locator('.modal-close, .el-dialog__headerbtn, [aria-label="Close"]').first.click(timeout=2000)
                    except Exception:
                        page.keyboard.press('Escape')
                page.wait_for_timeout(600)
            elif act['type'] == 'navigate_back':
                page.goto(f'{BASE_FE}/admin{path}', timeout=15000, wait_until='domcontentloaded')
                page.wait_for_timeout(1500)
            elif act['type'] == 'assert_modal':
                modal = page.locator('.modal-overlay, .el-dialog, .el-drawer').count()
                if modal == 0 and act.get('expected') == 'open':
                    problems.append(f'{act_name}: 弹窗未打开')
                if modal > 0 and act.get('expected') == 'closed':
                    problems.append(f'{act_name}: 弹窗未关闭')
            elif act['type'] == 'assert_url':
                if act['expected'] not in page.url:
                    problems.append(f'{act_name}: 未跳转到 {act["expected"]}，当前 {page.url}')
        except Exception as e:
            problems.append(f'{act_name}: 执行失败 - {str(e)[:120]}')

    status = 'OK'
    if console_errors:
        status = 'CONSOLE_ERR'
        problems.append(f'console: {console_errors[0][:200]}')
    if http_failures:
        status = status if status != 'OK' else 'HTTP_ERR'
        problems.append(f'http: {http_failures[0][:200]}')

    print(f'[{status}] {path} ({name})')
    for p in problems:
        print(f'    - {p}')
    return {'path': path, 'status': status, 'problems': problems}

def main():
    token = get_token()
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        results = []

        # Dashboard：点击统计卡片跳转
        results.append(check_page(page, '/dashboard', [
            {'type': 'click_selector', 'name': '统计卡片1', 'selector': '.stat-card, .dashboard-card, .card', 'wait': 1500},
            {'type': 'navigate_back', 'name': '返回仪表盘'},
        ], '仪表盘-跳转'))

        # 订单管理：搜索 + 详情跳转
        results.append(check_page(page, '/orders', [
            {'type': 'click_text', 'name': '搜索按钮', 'text': '搜索', 'wait': 1500},
            {'type': 'click_text', 'name': '重置按钮', 'text': '重置', 'wait': 1000},
            {'type': 'click_selector', 'name': '行内详情', 'selector': 'tbody tr .btn-primary, tbody tr a, tbody tr button', 'wait': 2000},
            {'type': 'assert_url', 'name': '详情跳转', 'expected': '/orders/'},
            {'type': 'navigate_back', 'name': '返回订单列表'},
        ], '订单管理-交互'))

        # 商品管理：搜索 + 编辑跳转
        results.append(check_page(page, '/products', [
            {'type': 'click_text', 'name': '搜索按钮', 'text': '搜索', 'wait': 1500},
            {'type': 'click_selector', 'name': '行内编辑', 'selector': 'tbody tr button', 'wait': 2000},
            {'type': 'assert_url', 'name': '编辑跳转', 'expected': '/products/edit/'},
            {'type': 'navigate_back', 'name': '返回商品列表'},
        ], '商品管理-交互'))

        # 用户管理：搜索
        results.append(check_page(page, '/users', [
            {'type': 'click_text', 'name': '搜索按钮', 'text': '搜索', 'wait': 1500},
            {'type': 'click_text', 'name': '重置按钮', 'text': '重置', 'wait': 1000},
        ], '用户管理-交互'))

        # 营销管理：跳转活动创建
        results.append(check_page(page, '/marketing', [
            {'type': 'click_selector', 'name': '新建活动按钮', 'selector': '.btn-primary, .el-button--primary', 'wait': 2000},
            {'type': 'assert_url', 'name': '活动创建跳转', 'expected': '/campaign'},
            {'type': 'navigate_back', 'name': '返回营销管理'},
        ], '营销管理-交互'))

        # CMS：新增弹窗
        results.append(check_page(page, '/cms', [
            {'type': 'click_selector', 'name': '新增按钮', 'selector': '.btn-primary, .el-button--primary', 'wait': 1200},
            {'type': 'assert_modal', 'name': '新增弹窗', 'expected': 'open'},
            {'type': 'close_modal', 'name': '关闭弹窗'},
            {'type': 'assert_modal', 'name': '弹窗已关', 'expected': 'closed'},
        ], 'CMS-弹窗'))

        # RBAC：新增角色弹窗
        results.append(check_page(page, '/rbac', [
            {'type': 'click_selector', 'name': '新增角色按钮', 'selector': '.btn-primary, .el-button--primary', 'wait': 1200},
            {'type': 'assert_modal', 'name': '新增弹窗', 'expected': 'open'},
            {'type': 'close_modal', 'name': '关闭弹窗'},
            {'type': 'assert_modal', 'name': '弹窗已关', 'expected': 'closed'},
        ], 'RBAC-弹窗'))

        # 财务：结算详情跳转
        results.append(check_page(page, '/finance', [
            {'type': 'click_selector', 'name': '查看全部结算', 'selector': '.link-btn, a', 'wait': 2000},
        ], '财务-跳转'))

        # 敏感词：新增弹窗
        results.append(check_page(page, '/sensitive-words', [
            {'type': 'click_selector', 'name': '新增按钮', 'selector': '.btn-primary, .el-button--primary', 'wait': 1200},
            {'type': 'assert_modal', 'name': '新增弹窗', 'expected': 'open'},
            {'type': 'close_modal', 'name': '关闭弹窗'},
        ], '敏感词-弹窗'))

        browser.close()

        print('\n================= 交互测试汇总 =================')
        ok = sum(1 for r in results if r['status'] == 'OK')
        print(f'OK={ok} / 其他={len(results)-ok} / TOTAL={len(results)}')

if __name__ == '__main__':
    main()
