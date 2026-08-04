"""
Check actual exception for the failing endpoints
"""
import json
import urllib.request
import urllib.error
import traceback

BASE = "http://localhost:8080"

def login():
    body = json.dumps({"email": "admin@moyuyo.com", "password": "moyuyo123"}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/admin/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read().decode("utf-8"))
            return data.get("data", {}).get("token")
    except urllib.error.HTTPError as e:
        print(f"Login HTTP {e.code}: {e.read().decode('utf-8')[:300]}")
        return None
    except Exception as e:
        print(f"Login error: {e}")
        return None

def call(token, method, path, body=None):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    body_str = json.dumps(body) if body else ""
    data = body_str.encode("utf-8") if body_str else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)

print("Logging in...")
token = login()
if not token:
    print("Login failed, trying with password 123456")
    body2 = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
    req2 = urllib.request.Request(
        f"{BASE}/api/admin/auth/login",
        data=body2,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req2, timeout=10) as r:
        data = json.loads(r.read().decode("utf-8"))
        print(f"Login response: {data}")
        token = data.get("data", {}).get("token")
print(f"Token: {token[:20] if token else 'None'}")

if not token:
    print("No token, aborting")
    exit(1)

# Test failing endpoints
endpoints = [
    ("POST", "/api/admin/order-ops/intercept/create", {"id": 1, "name": "test", "type": "test"}),
    ("POST", "/api/admin/order-ops/price-modify/create", {"id": 1, "name": "test", "type": "test", "adjustAmount": -10}),
    ("POST", "/api/admin/order-ops/print/record", {"id": 1, "name": "test", "type": "test"}),
]

for method, path, body in endpoints:
    code, resp = call(token, method, path, body)
    print(f"\n[{code}] {method} {path}")
    print(f"  Body sent: {body}")
    print(f"  Response: {resp[:300]}")
