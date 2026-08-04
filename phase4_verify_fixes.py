# -*- coding: utf-8 -*-
"""验证 3 个 bug 修复后的行为"""
import time
import urllib.request
import urllib.error
import json

def http(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    data = json.dumps(body).encode('utf-8') if body is not None else None
    req = urllib.request.Request(f'http://localhost:8080{path}', data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())
    except Exception as e:
        return 0, str(e)

# 登录
_, r = http('POST', '/api/admin/auth/login', {'email': 'admin@moyuyo.com', 'password': '123456'})
token = r['data']['token']
print('Logged in, token len=', len(token))
print()
print('===== 验证修复 =====')

cases = [
    # (desc, method, path, body, expected_code, label)
    ('push 无channel(原409)', 'POST', '/api/admin/push/create',
     {'title': 'fix1', 'content': 'c1', 'targetType': 'ALL'}, 400, '应返回400'),
    ('push 正常带channel', 'POST', '/api/admin/push/create',
     {'title': f'fix2-{int(time.time())}', 'content': 'c2', 'channel': 'NOTICE', 'targetType': 'ALL'}, 200, '应成功'),
    ('push 缺content', 'POST', '/api/admin/push/create',
     {'title': 'fix3', 'channel': 'NOTICE'}, 400, '应返回400'),
    ('push 缺title', 'POST', '/api/admin/push/create',
     {'content': 'c4', 'channel': 'NOTICE'}, 400, '应返回400'),

    ('order-tags 空body', 'POST', '/api/admin/order-tags/create', {}, 400, '应返回400'),
    ('order-tags 正常', 'POST', '/api/admin/order-tags/create',
     {'name': f'测试标签_{int(time.time())}', 'color': '#1890ff'}, 200, '应成功'),

    ('blacklist 空body', 'POST', '/api/admin/blacklist/create', {}, 400, '应返回400'),
    ('blacklist 缺value', 'POST', '/api/admin/blacklist/create', {'type': 'USER'}, 400, '应返回400'),
    ('blacklist 缺type', 'POST', '/api/admin/blacklist/create', {'value': 'v1'}, 400, '应返回400'),
    ('blacklist 正常', 'POST', '/api/admin/blacklist/create',
     {'type': 'USER', 'value': f'test_{int(time.time())}', 'reason': '测试'}, 200, '应成功'),

    ('blacklist/batch 空', 'POST', '/api/admin/blacklist/batch-create', [], 400, '应返回400'),
    ('blacklist/batch 缺字段1', 'POST', '/api/admin/blacklist/batch-create',
     [{'type': 'USER'}, {'value': 'v1'}], 400, '应返回400'),
    ('blacklist/batch 正常', 'POST', '/api/admin/blacklist/batch-create',
     [{'type': 'IP', 'value': f'1.2.3.{int(time.time()) % 200 + 10}'}], 200, '应成功'),
]

pass_n = fail_n = 0
for desc, method, path, body, expected, label in cases:
    code, resp = http(method, path, body, token=token)
    msg = resp.get('message', '') if isinstance(resp, dict) else str(resp)
    mark = 'PASS' if code == expected else 'FAIL'
    if code == expected:
        pass_n += 1
    else:
        fail_n += 1
    print(f'[{mark}] {desc}: {code} (期望{expected}) {label}')
    print(f'        msg: {msg}')

print()
print(f'汇总: 通过 {pass_n} / 失败 {fail_n} / 总 {len(cases)}')
