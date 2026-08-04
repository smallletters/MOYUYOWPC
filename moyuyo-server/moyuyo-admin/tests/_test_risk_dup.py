# -*- coding: utf-8 -*-
"""实测 /risk/rules 连续创建两次，验证 rule_code 冲突"""
import hmac, hashlib, base64, secrets, time, requests
from pathlib import Path

BASE = 'http://localhost:8080'
S = requests.Session()
ENV = {}
for line in Path(r'D:\MOYUYOWPC\moyuyo-server\.env').read_text(encoding='utf-8').splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    k, v = line.split('=', 1)
    ENV[k.strip()] = v.strip()
SIGN_SECRET = ENV.get('API_SIGN_SECRET', '')

def sign(method, path):
    ts = str(int(time.time())); nonce = secrets.token_hex(8)
    payload = f"{method}{path}{ts}{nonce}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(SIGN_SECRET.encode(), payload.encode(), hashlib.sha256).digest()
    return {'X-Sign': base64.b64encode(sig).decode(), 'X-Timestamp': ts, 'X-Nonce': nonce}

r = S.post(BASE + '/api/admin/auth/login', json={'email': 'admin@moyuyo.com', 'password': '123456'}, timeout=10)
token = r.json()['data']['token']
H = {'Authorization': f'Bearer {token}'}

for i in range(2):
    h = dict(H); h.update(sign('POST', '/api/admin/risk/rules'))
    rr = S.post(BASE + '/api/admin/risk/rules', json={
        'ruleName': f'API验收规则{i}', 'priority': 1, 'conditionJson': '{"op":"gt","times":5}',
        'action': 'REVIEW', 'enabled': True, 'ruleType': 'LOGIN'
    }, headers=h, timeout=10)
    print(f'第{i+1}次: {rr.status_code} {rr.text[:150]}')
print('done')
