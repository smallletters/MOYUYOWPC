# -*- coding: utf-8 -*-
"""检查 /orders 页面表格 DOM 结构与数据绑定"""
from playwright.sync_api import sync_playwright
import json, urllib.request

BASE_FE = 'http://localhost:5173'
LOGIN_URL = 'http://localhost:8080/api/admin/auth/login'

data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
req = urllib.request.Request(LOGIN_URL, data=data, method='POST', headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read())['data']['token']

with sync_playwright() as pw:
    b = pw.chromium.launch()
    ctx = b.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    page.add_init_script("localStorage.setItem('admin_token', '" + token + "')")
    page.goto(BASE_FE + '/admin/orders', timeout=20000, wait_until='domcontentloaded')
    page.wait_for_timeout(2500)
    info = page.evaluate('''() => {
      const tbody = document.querySelectorAll('tbody');
      const tables = document.querySelectorAll('table');
      const cards = document.querySelectorAll('.el-card');
      // 找含订单号的文本
      const bodyText = document.body.innerText.slice(0, 600);
      return {
        tbodyCount: tbody.length,
        tbodyFirstHTML: tbody.length ? tbody[0].innerHTML.slice(0, 300) : 'NONE',
        tableCount: tables.length,
        cardCount: cards.length,
        bodyTextStart: bodyText
      };
    }''')
    print(json.dumps(info, ensure_ascii=False, indent=1)[:1500])
    b.close()
print('done')
