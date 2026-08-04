import requests
from pathlib import Path

env = {}
p = Path(r'D:\MOYUYOWPC\moyuyo-server\.env')
if p.exists():
    for line in p.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        env[k.strip()] = v.strip()

s = requests.Session()
r = s.post('http://localhost:8080/api/admin/auth/login',
           json={'email': 'admin@moyuyo.com', 'password': '123456'}, timeout=5)
token = r.json().get('data', {}).get('token')
s.headers['Authorization'] = 'Bearer ' + token

# 测试各种 status 参数
for status_val in ['all', 'pending', 'processing', 'closed', 'overdue', '', 'PENDING', 'PROCESSING', 'CLOSED']:
    r = s.get(f'http://localhost:8080/api/admin/ticket/list?status={status_val}', timeout=5)
    data = r.json().get('data', {})
    records = data.get('records', [])
    total = data.get('total', 0)
    print(f'status={status_val!r}: total={total}, records={len(records)}')

# 测试 type 参数
for type_val in ['refund', 'complaint', 'consult', '', '退款', '投诉', '咨询']:
    r = s.get(f'http://localhost:8080/api/admin/ticket/list?type={type_val}', timeout=5)
    data = r.json().get('data', {})
    records = data.get('records', [])
    total = data.get('total', 0)
    print(f'type={type_val!r}: total={total}, records={len(records)}')

# 测试 priority 参数
for prio in ['high', 'medium', 'low', '', '高', '中', '低']:
    r = s.get(f'http://localhost:8080/api/admin/ticket/list?priority={prio}', timeout=5)
    data = r.json().get('data', {})
    records = data.get('records', [])
    total = data.get('total', 0)
    print(f'priority={prio!r}: total={total}, records={len(records)}')
