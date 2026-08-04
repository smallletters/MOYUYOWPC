"""
测试客服管理页面的 405 错误
"""
import requests
import json

BASE = "http://localhost:8080"

# 1. 登录获取 token
print("=" * 60)
print("步骤 1: 登录获取 token")
print("=" * 60)
login_data = {
    "email": "admin@moyuyo.com",
    "password": "123456"
}
r = requests.post(f"{BASE}/api/admin/auth/login", json=login_data, timeout=10)
print(f"登录 status: {r.status_code}")
print(f"登录 body: {r.text[:500]}")

if r.status_code != 200:
    print("登录失败，终止测试")
    exit(1)

resp = r.json()
token = resp.get("data", {}).get("token") or resp.get("token")
if not token:
    print(f"未找到 token，response: {json.dumps(resp, ensure_ascii=False)[:500]}")
    exit(1)
print(f"Token 获取成功: {token[:30]}...")

headers = {"Authorization": f"Bearer {token}"}

# 2. 测试 GET /api/admin/ticket/list
print("\n" + "=" * 60)
print("步骤 2: GET /api/admin/ticket/list")
print("=" * 60)
r = requests.get(f"{BASE}/api/admin/ticket/list", headers=headers, timeout=10)
print(f"status={r.status_code}")
print(f"body={r.text[:500]}")

# 3. 测试 GET /api/admin/ticket/stats
print("\n" + "=" * 60)
print("步骤 3: GET /api/admin/ticket/stats")
print("=" * 60)
r = requests.get(f"{BASE}/api/admin/ticket/stats", headers=headers, timeout=10)
print(f"status={r.status_code}")
print(f"body={r.text[:500]}")

# 4. 测试 GET /api/admin/ticket/list 带参数
print("\n" + "=" * 60)
print("步骤 4: GET /api/admin/ticket/list?status=pending")
print("=" * 60)
r = requests.get(f"{BASE}/api/admin/ticket/list?status=pending", headers=headers, timeout=10)
print(f"status={r.status_code}")
print(f"body={r.text[:500]}")

# 5. 测试 405 场景 - 错误的 HTTP 方法
print("\n" + "=" * 60)
print("步骤 5: POST /api/admin/ticket/list (应返回 405)")
print("=" * 60)
r = requests.post(f"{BASE}/api/admin/ticket/list", headers=headers, timeout=10)
print(f"status={r.status_code}")
print(f"body={r.text[:500]}")

# 6. 测试 OPTIONS 方法
print("\n" + "=" * 60)
print("步骤 6: OPTIONS /api/admin/ticket/list")
print("=" * 60)
r = requests.options(f"{BASE}/api/admin/ticket/list", headers=headers, timeout=10)
print(f"status={r.status_code}")
print(f"headers: {dict(r.headers)}")
print(f"body={r.text[:500]}")
