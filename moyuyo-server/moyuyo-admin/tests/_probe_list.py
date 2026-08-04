# -*- coding: utf-8 -*-
"""直接通过 SQL 探查数据库，看真实写入了什么"""
import json, urllib.request, urllib.error, time

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"


def http(method, url, token=None, body=None, timeout=8):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, {"_raw": txt[:200]}


# 1. 登录
s, p = http("POST", LOGIN_URL, body={"email": "admin@moyuyo.com", "password": "123456"})
token = p["data"]["token"]
print("login OK")

# 2. 创建敏感词
ts = int(time.time())
name = f"TEST_PROBE_{ts}"
print(f"\n=== Test 1: 敏感词 create+list+get ===")
print(f"create name: {name}")
s, p = http("POST", f"{ADMIN_BASE}/sensitive/create", token, {"word": name, "category": "其他", "action": "replace"})
print(f"create response: status={s}, data={p.get('data')}")

# 列出全部
s, p = http("GET", f"{ADMIN_BASE}/sensitive/list?page=1&size=100", token)
data = p.get("data") if isinstance(p, dict) else None
if isinstance(data, list):
    print(f"list total={len(data)}, latest 3: {data[-3:] if data else 'empty'}")
elif isinstance(data, dict):
    print(f"list data keys: {list(data.keys())}, value: {str(data)[:500]}")
else:
    print(f"list response: {p}")

# 精确查询 - 用 URL 编码后的 keyword
import urllib.parse
encoded = urllib.parse.quote(name)
s, p = http("GET", f"{ADMIN_BASE}/sensitive/list?keyword={encoded}", token)
data = p.get("data") if isinstance(p, dict) else None
if isinstance(data, list):
    print(f"with keyword(encoded={encoded}): list total={len(data)}, items={data[:3]}")
elif isinstance(data, dict):
    print(f"with keyword: data={str(data)[:500]}")

# 3. 创建黑名单
print(f"\n=== Test 2: 黑名单 create+list ===")
target = f"ph_test_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/blacklist/create", token, {"type": "user", "target": target, "reason": "probe"})
print(f"create response: status={s}, data={p.get('data')}")

# 列出全部（无 type 过滤）
s, p = http("GET", f"{ADMIN_BASE}/blacklist/list?page=1&size=20", token)
data = p.get("data") if isinstance(p, dict) else None
print(f"list full: data={str(data)[:800]}")
if isinstance(data, dict) and "list" in data:
    print(f"records: {data.get('list', [])[:5]}")

# 列出 type=user
s, p = http("GET", f"{ADMIN_BASE}/blacklist/list?type=user&page=1&size=20", token)
data = p.get("data") if isinstance(p, dict) else None
print(f"list type=user: data={str(data)[:500]}")
