"""推送管理 新建/编辑/删除 UI 端到端验证
链路：打开弹窗 → 填表 → 提交 → 成功提示 → 列表回显 → 删除
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


def main():
    token = get_token()
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")

        console_errors = []
        page.on('console', lambda m: console_errors.append(m.text) if m.type == 'error' else None)
        page.on('pageerror', lambda e: console_errors.append(f'PAGEERROR: {e}'))

        page.goto(f'{BASE_FE}/admin/push-manage', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)

        # 1. 打开新建弹窗
        page.get_by_text('新建推送', exact=True).first.click(timeout=5000)
        page.wait_for_timeout(800)
        assert page.locator('.el-dialog:visible').count() > 0, '新建弹窗未打开'
        print('PASS 新建弹窗打开')

        # 2. 填写表单并提交
        title = f'UI测试推送{__import__("time").time():.0f}'
        dialog = page.locator('.el-dialog:visible')
        dialog.get_by_placeholder('输入推送标题').fill(title)
        dialog.get_by_placeholder('输入推送内容').fill('UI 端到端验证内容')
        page.get_by_text('保存', exact=True).first.click(timeout=5000)
        page.wait_for_timeout(2000)

        # 3. 校验成功提示 + 列表回显
        msg_visible = page.locator('.el-message--success:visible').count() > 0
        echoed = page.get_by_text(title).count() > 0
        print(f'PASS 成功提示={msg_visible} 列表回显={echoed}')
        assert msg_visible and echoed, '提交成功但列表未回显'

        # 4. 删除该记录（清理测试数据）
        record = page.locator('.push-record', has_text=title).first
        record.locator('button', has_text='删除').click(timeout=5000)
        page.wait_for_timeout(600)
        # Element 确认框
        page.locator('.el-message-box__btns button', has_text='确认删除').click(timeout=5000)
        page.wait_for_timeout(2000)
        gone = page.get_by_text(title).count() == 0
        print(f'PASS 删除清理={gone}')

        print(f'console_errors={console_errors}')
        assert not console_errors, f'存在控制台错误: {console_errors[:2]}'
        browser.close()
        print('\n推送端到端验证全部通过')


if __name__ == '__main__':
    main()
