# -*- coding: utf-8 -*-
"""删除残留的 shipfix_ 测试数据"""
import json, urllib.request

BASE = 'http://localhost:8080/api/admin'
data = json.dumps({'email': 'admin@moyuyo.com', 'password': '123456'}).encode()
with urllib.request.urlopen(urllib.request.Request(f'{BASE}/auth/login', data=data, method='POST', headers={'Content-Type': 'application/json'}), timeout=8) as r:
    token = json.loads(r.read())['data']['token']
h = {'Authorization': f'Bearer {token}'}
with urllib.request.urlopen(urllib.request.Request(BASE + '/logistics/shipping-strategies?page=1&size=200', headers=h), timeout=8) as r:
    resp = json.loads(r.read())
deleted = 0
for rec in resp['data']:
    if str(rec.get('strategyName', '')).startswith('shipfix_'):
        tid = rec['id']
        req = urllib.request.Request(f'{BASE}/logistics/shipping-strategies/{tid}', method='DELETE', headers=h)
        try:
            urllib.request.urlopen(req, timeout=8)
            print(f"已删除 {rec['strategyName']} (id={tid})")
            deleted += 1
        except Exception as e:
            print(f'删除失败: {e}')
print(f'共删除 {deleted} 条')
