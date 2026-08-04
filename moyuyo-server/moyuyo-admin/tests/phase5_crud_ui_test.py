"""阶段2 UI 全链路测试：3 个页面 新建→提交→回显→清理"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import time

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        token = json.loads(r.read())['data']['token']

    ts = int(time.time())
    results = []

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

        # ===== 1. 敏感词（折叠表单）=====
        name1 = f'testword{ts}'
        page.goto(f'{BASE_FE}/admin/sensitive-words', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        try:
            page.locator('.sw-btn-primary').first.click()
            page.wait_for_timeout(800)
            form_open = page.locator('.sw-collapsible.open').count() > 0
            # 填写敏感词 + 替换词（必填）
            inputs = page.locator('.sw-collapsible.open .sw-form-input')
            inputs.nth(0).fill(name1)
            inputs.nth(1).fill('***')
            page.locator('.sw-collapsible.open').get_by_text('添加敏感词', exact=True).click()
            page.wait_for_timeout(2000)
            body = page.evaluate("() => document.body.innerText")
            shown = name1 in body
            # 清理：删除该敏感词
            row = page.locator('.sw-word-item', has_text=name1).first
            if row.count() > 0:
                row.locator('button', has_text='删除').first.click()
                page.wait_for_timeout(800)
                try:
                    page.get_by_text('确定', exact=True).click(timeout=2000)
                except Exception:
                    pass
                page.wait_for_timeout(1000)
            results.append(('敏感词', f'折叠展开={form_open} 回显={shown} 清理后存在={name1 in page.evaluate("() => document.body.innerText")}'))
        except Exception as e:
            results.append(('敏感词', f'异常: {str(e)[:120]}'))

        # ===== 2. 知识库（弹窗）=====
        name2 = f'测试文章{ts}'
        page.goto(f'{BASE_FE}/admin/knowledge-base', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        try:
            page.get_by_text('新建文章', exact=True).click()
            page.wait_for_timeout(800)
            dialog_visible = page.locator('.el-dialog').first.is_visible()
            page.locator('.el-dialog input').first.fill(name2)
            page.locator('.el-dialog').get_by_text('创建文章', exact=True).click()
            page.wait_for_timeout(2500)
            body = page.evaluate("() => document.body.innerText")
            shown = name2 in body
            # 清理：删除
            page.wait_for_timeout(1000)
            row = page.locator('tr', has_text=name2).first
            if row.count() > 0:
                row.locator('button', has_text='删除').first.click()
                page.wait_for_timeout(800)
                try:
                    page.get_by_text('确定', exact=True).click(timeout=2000)
                except Exception:
                    pass
                page.wait_for_timeout(1200)
            results.append(('知识库', f'弹窗={dialog_visible} 回显={shown}'))
        except Exception as e:
            results.append(('知识库', f'异常: {str(e)[:120]}'))

        # ===== 3. 优惠券（弹窗）=====
        name3 = f'测试券{ts}'
        page.goto(f'{BASE_FE}/admin/coupon-manage', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        try:
            page.get_by_text('新建优惠券', exact=True).click()
            page.wait_for_timeout(800)
            dialog_visible = page.locator('.el-dialog').first.is_visible()
            page.locator('.el-dialog input').first.fill(name3)
            page.locator('.el-dialog').get_by_text('保存', exact=True).click()
            page.wait_for_timeout(2500)
            body = page.evaluate("() => document.body.innerText")
            shown = name3 in body
            # 清理：删除
            page.wait_for_timeout(1000)
            row = page.locator('tr', has_text=name3).first
            if row.count() > 0:
                row.locator('button', has_text='删除').first.click()
                page.wait_for_timeout(800)
                try:
                    page.get_by_text('确定', exact=True).click(timeout=2000)
                except Exception:
                    pass
                page.wait_for_timeout(1200)
            results.append(('优惠券', f'弹窗={dialog_visible} 回显={shown}'))
        except Exception as e:
            results.append(('优惠券', f'异常: {str(e)[:120]}'))

        print('=== 全链路测试结果 ===')
        for page_name, detail in results:
            print(f'{page_name}: {detail}')
        print('console 错误数:', len(console_errors))
        for ce in console_errors[:5]:
            print('  ', ce[:150])
        print('http 失败数:', len(http_failures))
        for hf in http_failures[:5]:
            print('  ', hf[:150])
        browser.close()

if __name__ == '__main__':
    main()
