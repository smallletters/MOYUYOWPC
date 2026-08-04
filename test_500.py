import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import secrets
from pathlib import Path

BASE = "http://localhost:8080"
ENV = {}
env_path = Path(r"D:\MOYUYOWPC\moyuyo-server\.env")
if env_path.exists():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        ENV[k.strip()] = v.strip()

SIGN_SECRET = ENV.get("API_SIGN_SECRET", "")
print('SIGN_SECRET:', SIGN_SECRET[:10] if SIGN_SECRET else 'EMPTY')

# Login
body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
req = urllib.request.Request(
    f"{BASE}/api/admin/auth/login",
    data=body,
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=10) as r:
    data = json.loads(r.read().decode("utf-8"))
token = data["data"]["token"]
print('TOKEN:', token[:30])

# Test endpoints
test_paths = [
    ('POST', '/api/admin/order-tags/create', {'tagName': '测试标签', 'tagColor': '#FF0000', 'sortOrder': 1}),
    ('PUT', '/api/admin/order-tags/update', {'id': 1, 'tagName': '更新标签'}),
    ('PUT', '/api/admin/inventory-transfer/1/approve', {}),
    ('PUT', '/api/admin/inventory-transfer/1/complete', {}),
    ('PUT', '/api/admin/inventory-transfer/1/reject', {'reason': '测试'}),
    ('POST', '/api/admin/order-ops/print/record', {'orderId': 1, 'printType': 'PICK'}),
    ('POST', '/api/admin/order-ops/price-modify/create', {'orderId': 1, 'adjustAmount': -10, 'reason': '测试优惠'}),
    ('POST', '/api/admin/order-ops/intercept/create', {'orderId': 1, 'interceptType': 'MANUAL', 'reason': '测试拦截'}),
]

for method, path, body_data in test_paths:
    body_str = json.dumps(body_data) if body_data else ""
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    timestamp = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{timestamp}{nonce}{body_str}"
    if SIGN_SECRET:
        sig = hmac.new(SIGN_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
        headers["X-Sign"] = base64.b64encode(sig).decode("utf-8")
        headers["X-Timestamp"] = timestamp
        headers["X-Nonce"] = nonce
    data = body_str.encode("utf-8") if body_str else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            print(f"[{r.status}] {method} {path}")
            print(f"    {r.read().decode('utf-8')[:200]}")
    except urllib.error.HTTPError as e:
        print(f"[{e.code}] {method} {path}")
        print(f"    {e.read().decode('utf-8')[:300]}")
    except Exception as e:
        print(f"[ERR] {method} {path}: {e}")
    time.sleep(0.2)
