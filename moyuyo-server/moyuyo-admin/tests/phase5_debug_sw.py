"""调试敏感词 UI 提交链路"""
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
    name = f'uicheck{ts}'

    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()
        page.add_init_script(f"localStorage.setItem('admin_token', '{token}')")
        api_calls = []
        msgs = []
        page.on('request', lambda r: api_calls.append(r.url) if '/api/' in r.url and 'sensitive' in r.url else None)
        page.on('console', lambda m: msgs.append(f'[{m.type}] {m.text}') if m.type in ('error', 'warning') else None)

        page.goto(f'{BASE_FE}/admin/sensitive-words', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)

        # 展开表单
        page.locator('.sw-btn-primary').first.click()
        page.wait_for_timeout(800)
        print('折叠展开:', page.locator('.sw-collapsible.open').count() > 0)

        # 填写
        inputs = page.locator('.sw-collapsible.open .sw-form-input')
        print('表单输入框数量:', inputs.count())
        inputs.nth(0).fill(name)
        inputs.nth(1).fill('***')
        page.wait_for_timeout(300)

        # 提交
        page.locator('.sw-collapsible.open').get_by_text('添加敏感词', exact=True).click()
        page.wait_for_timeout(2500)

        print('sensitive API 调用:', api_calls)
        # 检查成功消息
        page.wait_for_timeout(500)
        print('页面当前文本含新词:', name in page.evaluate("() => document.body.innerText"))
        print('console 警告:', [m for m in msgs if '请输入' in m])

        # 重新加载页面检查
        page.reload(wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        print('刷新后含新词:', name in page.evaluate("() => document.body.innerText"))
        browser.close()

if __name__ == '__main__':
    main()
