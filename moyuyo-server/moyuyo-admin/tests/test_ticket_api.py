import requests, secrets, hashlib, hmac, base64, time
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
SECRET = env.get('API_SIGN_SECRET', '')
print('SIGN_SECRET:', repr(SECRET)[:30])

s = requests.Session()
r = s.post('http://localhost:8080/api/admin/auth/login',
           json={'email': 'admin@moyuyo.com', 'password': '123456'}, timeout=5)
print('login:', r.status_code, r.text[:200])
token = r.json().get('data', {}).get('token')
if not token:
    exit()


def sign(method, path, body=b''):
    ts = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f'{method}{path}{ts}{nonce}'.encode() + body
    sig = base64.b64encode(hmac.new(SECRET.encode(), payload,
                       hashlib.sha256).digest()).decode()
    return {'X-Sign': sig, 'X-Timestamp': ts, 'X-Nonce': nonce, 'Content-Type': 'application/json'}


s.headers['Authorization'] = 'Bearer ' + token

# 1. ticket list
r1 = s.get('http://localhost:8080/api/admin/ticket/list',
           headers=sign('GET', '/api/admin/ticket/list'), timeout=5)
print('ticket list:', r1.status_code, r1.text[:300])

# 2. ticket stats
r2 = s.get('http://localhost:8080/api/admin/ticket/stats',
           headers=sign('GET', '/api/admin/ticket/stats'), timeout=5)
print('ticket stats:', r2.status_code, r2.text[:300])

# 3. ticket assign
body = b'{"assigneeId":"1"}'
r3 = s.put('http://localhost:8080/api/admin/ticket/1/assign',
           data=body, headers=sign('PUT', '/api/admin/ticket/1/assign', body), timeout=5)
print('ticket assign:', r3.status_code, r3.text[:300])

# 4. ticket status
body2 = b'{"status":"PROCESSING"}'
r4 = s.put('http://localhost:8080/api/admin/ticket/1/status',
           data=body2, headers=sign('PUT', '/api/admin/ticket/1/status', body2), timeout=5)
print('ticket status:', r4.status_code, r4.text[:300])

# 5. ticket reply
body3 = b'{"content":"test reply"}'
r5 = s.post('http://localhost:8080/api/admin/ticket/1/reply',
            data=body3, headers=sign('POST', '/api/admin/ticket/1/reply', body3), timeout=5)
print('ticket reply:', r5.status_code, r5.text[:300])

# 6. ticket detail
r6 = s.get('http://localhost:8080/api/admin/ticket/1',
           headers=sign('GET', '/api/admin/ticket/1'), timeout=5)
print('ticket detail:', r6.status_code, r6.text[:300])
