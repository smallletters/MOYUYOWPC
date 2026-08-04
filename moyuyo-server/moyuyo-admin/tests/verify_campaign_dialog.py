# -*- coding: utf-8 -*-
"""阶段4：/campaign 弹窗专项验证"""
from playwright.sync_api import sync_playwright
import json
import urllib.request

LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'


def main():
    data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
    req = urllib.request.Request(LOGIN_URL, data=data, method='POST',
                                 headers={'Content-Type': 'application/json'})
    token = json.loads(urllib.request.urlopen(req).read())['data']['token']
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        ctx = b.new_context(viewport={'width': 1440, 'height': 900})
        p = ctx.new_page()
        p.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
        p.goto('http://localhost:5173/admin/campaign', timeout=20000, wait_until='domcontentloaded')
        p.wait_for_timeout(1500)
        # 切到活动管理 Tab 点击编辑打开弹窗
        tabs = p.locator('.el-tabs__item, .tab-item, [role=tab]').all()
        for t in tabs:
            try:
                txt = (t.inner_text() or '').strip()
                if '管理' in txt:
                    t.click(timeout=3000)
                    p.wait_for_timeout(800)
                    break
            except Exception:
                pass
        # 找编辑按钮
        edit_btns = p.locator('button:has-text("编辑")').all()
        if edit_btns:
            edit_btns[0].click(timeout=3000)
            p.wait_for_timeout(800)
            dlg = p.locator('.el-dialog').first
            visible = dlg.count() > 0 and dlg.is_visible()
            print('DIALOG_OPENED:', visible)
            if visible:
                # 取消关闭
                cancel = p.locator('.el-dialog button:has-text("取消")').first
                if cancel.count() > 0:
                    cancel.click(timeout=3000)
                    p.wait_for_timeout(500)
                    print('DIALOG_CLOSED:', not dlg.is_visible())
        else:
            print('NO_EDIT_BTN (可能表格为空)')
        b.close()


if __name__ == '__main__':
    main()
