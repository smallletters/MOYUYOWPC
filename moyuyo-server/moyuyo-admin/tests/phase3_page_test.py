# -*- coding: utf-8 -*-
"""阶段3：使用 Playwright 跑全量管理页面测试"""
from playwright.sync_api import sync_playwright
import json
import re
import time

BASE = 'http://localhost:8080/admin'
ADMIN_ROUTER = r'd:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\router\index.js'

# 从 router/index.js 提取所有路由
def extract_routes():
    routes = []
    with open(ADMIN_ROUTER, 'r', encoding='utf-8') as f:
        content = f.read()
    # 匹配 path: 'xxx' 模式（排除特殊路径如 :pathMatch(.*)*）
    pattern = re.compile(r"path:\s*'([^']+)'(?=[\s,])")
    for m in pattern.finditer(content):
        p = m.group(1)
        # 跳过通配符
        if ':' in p and 'pathMatch' in p:
            continue
        routes.append(p)
    return routes

def get_token():
    """通过 API 直接获取登录 token"""
    import urllib.request
    import urllib.error
    url = 'http://localhost:8080/api/admin/auth/login'
    data = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST', headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            payload = json.loads(r.read().decode('utf-8'))
            if payload.get('code') == 0:
                return payload['data']['token']
    except Exception as e:
        print(f"  [token获取失败] {e}")
    return None


def _sign(method, path):
    """生成签名头（用于获取真实 ID）"""
    import hmac, hashlib, base64, secrets, time
    from pathlib import Path
    env_path = Path(r'D:\MOYUYOWPC\moyuyo-server\.env')
    secret = ''
    if env_path.exists():
        for line in env_path.read_text(encoding='utf-8').splitlines():
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            if k.strip() == 'API_SIGN_SECRET':
                secret = v.strip()
    ts = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{ts}{nonce}"
    if not secret:
        return {}
    sig = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest()
    return {
        'X-Sign': base64.b64encode(sig).decode(),
        'X-Timestamp': ts,
        'X-Nonce': nonce,
    }


def fetch_real_id(token, list_path):
    """从列表接口取第一条记录的 id（处理 records / list 两种字段）"""
    import urllib.request
    headers = {'Authorization': f'Bearer {token}'}
    headers.update(_sign('GET', list_path))
    req = urllib.request.Request(f'http://localhost:8080{list_path}', headers=headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            data = json.loads(r.read().decode('utf-8'))
            inner = data.get('data', {})
            records = inner.get('records') or inner.get('list') or []
            if records:
                return records[0].get('id')
    except Exception:
        pass
    return None


def resolve_path(path, token):
    """把含 :id/:xxx 的路径替换为真实 ID，参数化页面避免 400 误报"""
    import re
    if ':' not in path:
        return path
    # 为不同实体准备列表接口
    list_map = {
        'orders': '/api/admin/orders/list?page=1&size=1',
        'products': '/api/admin/products/list?page=1&size=1',
    }
    for key, list_path in list_map.items():
        if path.startswith(key + '/'):
            real_id = fetch_real_id(token, list_path)
            if real_id is not None:
                return path.replace(':' + path.split(':')[1].split('/')[0], str(real_id))
    return path

def login(page):
    """通过 API 注入 token 绕过登录界面"""
    token = get_token()
    if not token:
        return False
    # 打开任意页面以写入 localStorage
    page.goto(f'{BASE}/login')
    page.wait_for_load_state('networkidle')
    time.sleep(1)
    page.evaluate(f"localStorage.setItem('admin_token', '{token}')")
    time.sleep(0.5)
    # 跳转到一个受保护页面验证
    page.goto(f'{BASE}/dashboard')
    page.wait_for_load_state('networkidle')
    time.sleep(2)
    return '/login' not in page.url

def test_page(page, path):
    """测试单个页面"""
    errors = []
    console_errors = []
    page_errors = []

    # 监听控制台错误
    def on_console(msg):
        if msg.type == 'error':
            console_errors.append(msg.text[:200])

    def on_pageerror(err):
        page_errors.append(str(err)[:200])

    page.on('console', on_console)
    page.on('pageerror', on_pageerror)

    url = f'{BASE}/{path}' if path else f'{BASE}/dashboard'
    try:
        page.goto(url, wait_until='domcontentloaded', timeout=15000)
    except Exception as e:
        page.remove_listener('console', on_console)
        page.remove_listener('pageerror', on_pageerror)
        return {
            'path': path,
            'url': url,
            'status': 'TIMEOUT',
            'ok': False,
            'error': str(e)[:200],
            'console_errors': [],
            'page_errors': []
        }

    try:
        page.wait_for_load_state('networkidle', timeout=10000)
    except Exception:
        pass
    time.sleep(1)

    # 检查页面是否有主要内容
    has_content = False
    try:
        # 至少有一个按钮或表或表单
        if page.locator('button, table, .el-table, form, .card, .el-card').count() > 0:
            has_content = True
    except Exception:
        pass

    # 检测 Vue 编译错误
    html = page.content()
    vue_error = bool(re.search(r'\[plugin:vite:vue[^"]*\]|TypeError:|SyntaxError:|Failed to fetch dynamically imported module', html[:5000]))

    page.remove_listener('console', on_console)
    page.remove_listener('pageerror', on_pageerror)

    # 严重错误判定
    critical = vue_error or len(page_errors) > 0
    status = 'WARN' if (console_errors or not has_content) and not critical else 'OK'
    if critical:
        status = 'ERR'

    return {
        'path': path,
        'url': page.url,
        'status': status,
        'ok': not critical,
        'buttons': page.locator('button:visible').count(),
        'console_errors': console_errors[:3],
        'page_errors': page_errors[:3],
        'has_content': has_content,
        'vue_error': vue_error
    }

def main():
    print("=" * 80)
    print("【阶段3：Playwright 全量管理页面测试】")
    print("=" * 80)

    routes = extract_routes()
    # 去掉重复和空路径
    unique_routes = []
    seen = set()
    for r in routes:
        if r and r not in seen:
            seen.add(r)
            unique_routes.append(r)
    print(f"\n[扫描] 共发现 {len(unique_routes)} 个路由\n")

    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            executable_path=r'C:\Users\ASUS\AppData\Local\ms-playwright\chromium-1223\chrome-win64\chrome.exe'
        )
        ctx = browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = ctx.new_page()

        # 登录
        if not login(page):
            print("[错误] 登录失败，无法继续测试")
            browser.close()
            return
        print(f"[登录成功] URL={page.url}\n")

        # 测试所有页面（参数化路径自动用真实 ID 替换，避免 400 误报）
        token = get_token()
        for i, path in enumerate(unique_routes):
            real_path = resolve_path(path, token) if token else path
            r = test_page(page, real_path)
            r['original_path'] = path
            r['tested_path'] = real_path
            results.append(r)
            tag = r['status']
            btn = r.get('buttons', 0)
            err_msg = ''
            if r.get('page_errors'):
                err_msg = f" | err={r['page_errors'][0][:80]}"
            elif r.get('vue_error'):
                err_msg = " | vue编译错误"
            print(f"[{tag:4s}] {i+1:3d}/{len(unique_routes)} {path:40s} btn={btn}{err_msg}")

        browser.close()

    # 汇总
    print(f"\n{'=' * 80}")
    ok_cnt = sum(1 for r in results if r['status'] == 'OK')
    warn_cnt = sum(1 for r in results if r['status'] == 'WARN')
    err_cnt = sum(1 for r in results if r['status'] == 'ERR')
    print(f"【阶段3 总结】 {ok_cnt} OK / {warn_cnt} WARN / {err_cnt} ERR / {len(results)} TOTAL")
    print(f"{'=' * 80}")

    if err_cnt > 0:
        print("\n=== 错误页面详情 ===")
        for r in results:
            if r['status'] == 'ERR':
                print(f"- {r['path']}")
                for e in r.get('page_errors', []):
                    print(f"    pageerror: {e[:200]}")
                if r.get('vue_error'):
                    print(f"    vue: 编译/加载错误")

    # 输出 JSON 报告
    with open(r'd:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\phase3_output.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n[详细报告] d:\\MOYUYOWPC\\moyuyo-server\\moyuyo-admin\\tests\\phase3_output.json")

if __name__ == '__main__':
    main()
