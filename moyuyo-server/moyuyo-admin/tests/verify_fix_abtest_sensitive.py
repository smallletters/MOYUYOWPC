# -*- coding: utf-8 -*-
"""验证 AbTest 编辑弹窗 与 SensitiveWords 按钮歧义修复（2026-08-01）"""
from playwright.sync_api import sync_playwright
import json
import urllib.request
import time

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'


def login():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']


def main():
    token = login()
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

        # ========== 1. AbTest 编辑弹窗验证（修正定位：按标题判断，而非 DOM 顺序） ==========
        print('=== AbTest 编辑弹窗验证 ===')
        page.goto(f'{BASE_FE}/admin/ab-test', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        # 点击第一行「编辑」
        first_edit = page.locator('.el-table__row button', has_text='编辑').first
        if first_edit.count() == 0:
            print('编辑按钮不存在（可能无数据）')
        else:
            first_edit.click()
            page.wait_for_timeout(1000)
            # 编辑弹窗标题为「编辑实验」，按标题定位
            edit_dialog = page.locator('.el-dialog', has_text='编辑实验').first
            edit_visible = edit_dialog.is_visible() if edit_dialog.count() > 0 else False
            print(f'编辑弹窗(标题=编辑实验)可见: {edit_visible}')
            if edit_visible:
                # 验证表单回填
                name_val = page.locator('.el-dialog', has_text='编辑实验').locator('.el-input input').first.input_value()
                print(f'表单回填实验名称: {name_val!r}')
                page.keyboard.press('Escape')
                page.wait_for_timeout(500)
        print()

        # ========== 2. SensitiveWords 按钮歧义验证 ==========
        print('=== SensitiveWords 新增按钮验证 ===')
        page.goto(f'{BASE_FE}/admin/sensitive-words', timeout=20000, wait_until='domcontentloaded')
        page.wait_for_timeout(2500)
        btn = page.get_by_role('button', name='新增敏感词', exact=True)
        print(f'「新增敏感词」按钮数量: {btn.count()}（应=1）')
        btn.click()
        page.wait_for_timeout(800)
        form_open = page.locator('.sw-collapsible-body.open').is_visible() or page.locator('.sw-collapsible.open').count() > 0
        print(f'新增表单展开: {form_open}')
        # 填写表单并提交
        page.locator('.sw-form-input').first.fill(f'验证词{int(time.time())}')
        page.locator('.sw-form-input').nth(1).fill('***')
        page.get_by_role('button', name='添加敏感词', exact=True).click()
        page.wait_for_timeout(2500)
        success = page.locator('.el-message--success').first
        print(f'提交成功提示: {success.inner_text().strip() if success.is_visible() else "未见成功提示"}')
        # 回显验证
        body = page.evaluate("() => document.body.innerText")
        print(f'表单已关闭: {page.locator(".sw-collapsible.open").count() == 0}')
        print()

        print('--- console 错误 ---')
        for ce in console_errors[:8]:
            print(ce[:200])
        print('--- http 失败 ---')
        for hf in http_failures[:8]:
            print(hf[:200])
        browser.close()


if __name__ == '__main__':
    main()
