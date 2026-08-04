"""AbTest 专项验证：新建实验完整链路"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        token = json.loads(r.read())['data']['token']

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        console_errors = []
        http_failures = []
        page.on('console', lambda m: console_errors.append(m.text) if m.type == 'error' else None)
        page.on('pageerror', lambda e: console_errors.append(f'PAGEERROR: {e}'))
        page.on('response', lambda r: http_failures.append(f'{r.status} {r.url}') if r.status >= 400 and '/api/' in r.url else None)

        page.goto(f'{BASE_FE}/admin/ab-test', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        print(f'页面加载 body_len={page.evaluate("() => document.body.innerText.length")}')

        # 表格列验证：检查真实字段是否渲染
        table_text = page.locator('.el-table').inner_text()
        print('--- 表格内容(前200字) ---')
        print(table_text[:200])
        print('包含 group 字段列:', 'A 组' in table_text)

        # 1. 点击新建实验
        page.get_by_text('新建实验', exact=True).click()
        page.wait_for_timeout(1000)
        dialog_visible = page.locator('.el-dialog').first.is_visible()
        print(f'新建弹窗打开: {dialog_visible}')

        # 2. 填写表单
        name = f'自动化测试实验-{int(__import__("time").time())}'
        page.locator('.el-dialog .el-input input').first.fill(name)
        page.locator('.el-dialog textarea').fill('Playwright 自动化创建验证')
        # 设置转化率
        page.wait_for_timeout(300)
        print(f'表单已填写: {name}')

        # 3. 提交
        page.locator('.el-dialog').get_by_text('保存', exact=True).click()
        page.wait_for_timeout(2500)
        success_msg = page.locator('.el-message--success').first
        if success_msg.is_visible():
            print(f'提交成功提示: {success_msg.inner_text()}')
        else:
            print('提交后未见成功提示!')

        # 4. 回显验证：列表中出现新记录
        page.wait_for_timeout(1500)
        body = page.evaluate("() => document.body.innerText")
        print(f'列表回显新实验: {name in body}')

        # 5. 详情弹窗
        try:
            page.locator('tr', has_text=name).locator('button', has_text='详情').click()
            page.wait_for_timeout(1000)
            detail_visible = page.locator('.el-dialog').last.is_visible()
            print(f'详情弹窗打开: {detail_visible}')
            detail_text = page.locator('.el-dialog').last.inner_text()
            print(f'详情内容包含名称: {name in detail_text}')
            # 关闭详情
            page.keyboard.press('Escape')
            page.wait_for_timeout(600)
        except Exception as e:
            print(f'详情验证异常: {e}')

        # 6. 编辑弹窗
        try:
            page.locator('tr', has_text=name).locator('button', has_text='编辑').click()
            page.wait_for_timeout(1000)
            edit_visible = page.locator('.el-dialog').last.is_visible()
            print(f'编辑弹窗打开: {edit_visible}')
            page.keyboard.press('Escape')
        except Exception as e:
            print(f'编辑验证异常: {e}')

        print('--- console 错误 ---')
        for ce in console_errors[:5]:
            print(ce[:150])
        print('--- http 失败 ---')
        for hf in http_failures[:5]:
            print(hf[:150])

        browser.close()

if __name__ == '__main__':
    main()
