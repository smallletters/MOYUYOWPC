"""验证 B.3：用 unique type 创建活动，看是否能出现在 list"""
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
            return e.code, {"_raw": txt[:500]}


token = http("POST", LOGIN_URL, body={"email": "admin@moyuyo.com", "password": "123456"})[1]["data"]["token"]

ts = int(time.time())
unique_type = f"PROBE_{ts}"
# 用全新的 type
s, p = http("POST", f"{ADMIN_BASE}/points/activities/create", token, {
    "name": "测试活动", "type": unique_type, "points": 100,
    "dailyLimit": 1, "status": 1,
    "startTime": "2026-07-30T00:00:00", "endTime": "2027-12-31T23:59:59"
})
print(f"create type={unique_type}: {p.get('data')}")

s, p = http("GET", f"{ADMIN_BASE}/points/activities?page=1&size=50", token)
data = p.get("data", {})
items = data.get("records") if isinstance(data, dict) else data
print(f"list total={len(items) if items else 0}")
if items:
    types = [it.get("type") for it in items]
    names = [it.get("name") for it in items]
    print(f"types: {types}")
    print(f"names: {names}")
    found = unique_type in types
    print(f"包含 {unique_type}: {found}")
