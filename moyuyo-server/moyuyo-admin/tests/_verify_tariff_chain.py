# -*- coding: utf-8 -*-
"""实测关税配置完整链路：创建 -> 回显 -> 编辑 -> 试算 -> 删除"""
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

def call(method, url, body=None):
    h = dict(H); h.update(sign(method, url))
    r = S.request(method, BASE + '/api/admin' + url, json=body, headers=h, timeout=10)
    print(f'{method} {url} -> {r.status_code} {r.text[:250]}')
    return r.json()

# 1. 创建（新字段）
r1 = call('POST', '/tariff/configs/create', {
    'productCategory': '电子产品', 'countryCode': 'JP', 'rate': 10,
    'minThreshold': 0, 'maxThreshold': 1000, 'currency': 'JPY', 'status': 'ENABLED'
})
# 2. 列表回显
data = call('GET', '/tariff/configs')
records = (data.get('data') or {}).get('records') or []
target = next((x for x in records if x.get('countryCode') == 'JP'), None)
print('回显检查: productCategory=%s countryCode=%s rate=%s min=%s max=%s currency=%s status=%s' % (
    target.get('productCategory') if target else None,
    target.get('countryCode') if target else None,
    target.get('rate') if target else None,
    target.get('minThreshold') if target else None,
    target.get('maxThreshold') if target else None,
    target.get('currency') if target else None,
    target.get('status') if target else None))
cid = target.get('id') if target else None
# 3. 编辑
if cid:
    call('PUT', '/tariff/configs/update', {'id': cid, 'rate': 12, 'maxThreshold': 2000})
# 4. 试算
calc = call('POST', '/tariff/calculate', {'countryCode': 'JP', 'category': '电子产品', 'amount': 500})
print('试算结果 tariff:', (calc.get('data') or {}).get('tariff'))
# 5. 删除
if cid:
    call('DELETE', f'/tariff/configs/{cid}')
print('完成')
