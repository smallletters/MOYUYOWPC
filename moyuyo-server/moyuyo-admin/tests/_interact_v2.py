# 阶段2 交互验证 v2：基于真实 DOM 的按钮/弹窗探测
# 逻辑：加载页面 -> 找"新建/新增/创建/添加"按钮并点击 -> 检查弹窗 -> 关闭弹窗 -> 检查行内操作
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

# 核心页面（覆盖设计稿全部一级模块）
PAGES = [
    ('/dashboard', '仪表盘'),
    ('/orders', '订单管理'),
    ('/products', '商品管理'),
    ('/users', '用户管理'),
    ('/marketing', '营销管理'),
    ('/cms', 'CMS管理'),
    ('/rbac', 'RBAC管理'),
    ('/finance', '财务概览'),
    ('/inventory', '库存管理'),
    ('/sensitive-words', '敏感词管理'),
    ('/ab-test', 'A/B测试'),
    ('/coupon-manage', '优惠券管理'),
    ('/knowledge-base', '知识库'),
    ('/system-config', '系统配置'),
    ('/tariff', '关税管理'),
    ('/blacklist', '黑名单管理'),
    ('/order-tags', '订单标签'),
    ('/points-manage', '积分管理'),
]

CREATE_TEXT = ['新建', '新增', '创建', '添加', '新增实验', '新建 Banner', '新增推送']
CLOSE_TEXT = ['取消', '关闭']


def get_token():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']


def check_page(page, path, name):
    console_errors = []
    http_failures = []

    def on_console(msg):
        if msg.type == 'error':
            console_errors.append(msg.text)

    def on_pageerror(exc):
        console_errors.append(f'PAGEERROR: {exc}')

    def on_response(resp):
        if resp.status >= 400 and '/api/' in resp.url:
            http_failures.append(f'{resp.status} {resp.url}')

    page.on('console', on_console)
    page.on('pageerror', on_pageerror)
    page.on('response', on_response)

    page.goto(f'{BASE_FE}/admin{path}', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(2500)

    problems = []
    buttons_info = []

    # 1. 找出页面所有按钮文本
    try:
        btns = page.evaluate("""() => {
            const out = [];
            document.querySelectorAll('button, .btn, .el-button').forEach(b => {
                const t = (b.innerText || '').trim().replace(/\\s+/g, ' ');
                if (t && t.length < 12) out.push({ text: t, disabled: b.disabled || b.classList.contains('is-disabled') });
            });
            return out;
        }""")
        buttons_info = btns
    except Exception:
        pass

    # 2. 尝试点击创建类按钮，检查弹窗
    create_btn = None
    for b in buttons_info:
        if any(k in b['text'] for k in CREATE_TEXT) and not b['disabled']:
            create_btn = b['text']
            break

    if create_btn:
        try:
            page.get_by_text(create_btn, exact=True).first.click(timeout=4000)
            page.wait_for_timeout(1200)
            modal_count = page.locator('.el-dialog, .el-drawer, .modal-overlay').count()
            if modal_count > 0:
                # 有弹窗，尝试关闭
                closed = False
                for c in CLOSE_TEXT:
                    try:
                        page.get_by_text(c, exact=True).first.click(timeout=2000)
                        closed = True
                        break
                    except Exception:
                        continue
                if not closed:
                    try:
                        page.locator('.el-dialog__headerbtn, .el-drawer__close-btn').first.click(timeout=2000)
                        closed = True
                    except Exception:
                        page.keyboard.press('Escape')
                page.wait_for_timeout(800)
                modal_after = page.locator('.el-dialog:visible, .el-drawer:visible').count()
                status = 'MODAL_OK' if modal_after == 0 else 'MODAL_STUCK'
            else:
                # 无弹窗：可能为跳转（如 /marketing 创建活动）
                status = 'NO_MODAL'
            print(f'[CREATE:{status:10s}] {path} ({name}) 按钮=[{create_btn}] 弹窗数={modal_count if "modal_count" in dir() else "-"}')
        except Exception as e:
            print(f'[CREATE:CLICK_FAIL] {path} ({name}) 按钮=[{create_btn}] {str(e)[:100]}')
    else:
        print(f'[CREATE:NONE      ] {path} ({name}) 按钮列表={[b["text"] for b in buttons_info][:8]}')

    # 3. 行内操作（表格首行操作按钮）
    try:
        row_btns = page.evaluate("""() => {
            const out = [];
            document.querySelectorAll('.el-table__body button, .el-table__body .btn, .el-table__body a, tbody button, tbody .btn').forEach(b => {
                const t = (b.innerText || '').trim();
                if (t && t.length < 8) out.push(t);
            });
            return out;
        }""")
        if row_btns:
            target = next((t for t in row_btns if any(k in t for k in ['编辑', '详情', '查看', '审核', '终止'])), row_btns[0])
            try:
                page.get_by_text(target, exact=True).first.click(timeout=3000)
                page.wait_for_timeout(1000)
                modal2 = page.locator('.el-dialog:visible, .el-drawer:visible').count()
                url_changed = '/orders/' in page.url or '/products/edit' in page.url
                ok = modal2 > 0 or url_changed
                print(f'[ROW:{"OK    " if ok else "NO_FEEDBACK"}] {path} ({name}) 操作=[{target}] 弹窗数={modal2} url={page.url[-40:]}')
            except Exception as e:
                print(f'[ROW:FAIL       ] {path} ({name}) 操作=[{target}] {str(e)[:100]}')
    except Exception:
        pass

    # 汇总
    if console_errors:
        print(f'    !! CONSOLE_ERR: {console_errors[0][:180]}')
    if http_failures:
        print(f'    !! HTTP_ERR: {http_failures[0][:180]}')

    page.remove_listener('console', on_console)
    page.remove_listener('pageerror', on_pageerror)
    page.remove_listener('response', on_response)
    return {'path': path, 'name': name, 'console': console_errors, 'http': http_failures}


def main():
    token = get_token()
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        for path, name in PAGES:
            check_page(page, path, name)
        browser.close()
    print('\n阶段2 交互探测完成')


if __name__ == '__main__':
    main()
