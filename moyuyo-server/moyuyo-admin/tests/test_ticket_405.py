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

# 测试各种 405 场景
# 1. POST 到 GET endpoint
print('--- POST /ticket/list ---')
r = s.post('http://localhost:8080/api/admin/ticket/list', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 2. DELETE to GET endpoint
print('--- DELETE /ticket/list ---')
r = s.delete('http://localhost:8080/api/admin/ticket/list', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 3. PATCH to ticket
print('--- PATCH /ticket/1 ---')
r = s.patch('http://localhost:8080/api/admin/ticket/1', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 4. PUT to GET-only endpoint
print('--- PUT /ticket/list ---')
r = s.put('http://localhost:8080/api/admin/ticket/list', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 5. POST to /ticket/{id} (only GET allowed)
print('--- POST /ticket/1 ---')
r = s.post('http://localhost:8080/api/admin/ticket/1', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 6. PUT to /ticket/{id} (no method)
print('--- PUT /ticket/1 ---')
r = s.put('http://localhost:8080/api/admin/ticket/1', json={}, timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 7. POST to /ticket/{id}/assign (PUT only)
print('--- POST /ticket/1/assign ---')
r = s.post('http://localhost:8080/api/admin/ticket/1/assign', json={'assigneeId': '1'}, timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 8. POST to /ticket/{id}/status (PUT only)
print('--- POST /ticket/1/status ---')
r = s.post('http://localhost:8080/api/admin/ticket/1/status', json={'status': 'PROCESSING'}, timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 9. GET to /ticket/{id}/assign (PUT only)
print('--- GET /ticket/1/assign ---')
r = s.get('http://localhost:8080/api/admin/ticket/1/assign', timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')

# 10. 不存在的 path
print('--- POST /ticket/foo/assign ---')
r = s.post('http://localhost:8080/api/admin/ticket/foo/assign', json={'assigneeId': '1'}, timeout=5)
print(f'  status={r.status_code}, body={r.text[:200]}')
