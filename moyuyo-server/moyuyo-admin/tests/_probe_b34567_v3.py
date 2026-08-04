# -*- coding: utf-8 -*-
"""精确判断 B.3 B.5 的 create 是否真的写库"""
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


def login():
    s, p = http("POST", LOGIN_URL, body={"email": "admin@moyuyo.com", "password": "123456"})
    return p["data"]["token"] if s == 200 and p.get("code") == 0 else None


token = login()
ts = int(time.time())

# === B.3 积分活动：列出所有活动，看是否真的写入了 ===
print("=" * 60)
print("B.3 积分活动 - 创建后立即 list 全量")
for variant in [
    {"name": f"P_{ts}_v1", "type": "SIGN_IN", "points": 1, "dailyLimit": 1, "status": 1},
    {"activityName": f"P_{ts}_v2", "type": "SIGN_IN", "points": 1, "dailyLimit": 1, "status": 1, "startTime": "2026-07-30T00:00:00", "endTime": "2027-12-31T23:59:59"},
]:
    s, p = http("POST", f"{ADMIN_BASE}/points/activities/create", token, variant)
    print(f"  variant={list(variant.keys())[:3]}: create -> {p.get('data')}")
s, p = http("GET", f"{ADMIN_BASE}/points/activities?page=1&size=50", token)
data = p.get("data") if isinstance(p, dict) else None
items = data if isinstance(data, list) else (data.get("records") or data.get("list") or []) if isinstance(data, dict) else []
print(f"  list 全量: total={len(items)}")
if items:
    print(f"  names: {[it.get('name') for it in items]}")

# === B.5 关税配置：列出所有配置 ===
print("\n" + "=" * 60)
print("B.5 关税配置 - 创建后立即 list 全量")
for variant in [
    {"code": f"PR_{ts}_v1", "name": "测试1", "rate": 0.1, "country": "CN"},
    {"countryCode": f"PR_{ts}_v2", "rate": 0.1, "name": "测试2"},
    {"code": f"PR_{ts}_v3", "rate": 0.1, "countryCode": "CN"},
]:
    s, p = http("POST", f"{ADMIN_BASE}/tariff/configs/create", token, variant)
    print(f"  variant={list(variant.keys())}: create -> {p.get('data')}")
s, p = http("GET", f"{ADMIN_BASE}/tariff/configs?page=1&size=50", token)
data = p.get("data") if isinstance(p, dict) else None
items = data if isinstance(data, list) else (data.get("records") or data.get("list") or []) if isinstance(data, dict) else []
print(f"  list 全量: total={len(items)}")
if items:
    print(f"  codes/countryCodes: {[(it.get('code') or it.get('countryCode')) for it in items][-10:]}")

# === B.6 风险预警：用正确的字段名（name）查 ===
print("\n" + "=" * 60)
print("B.6 风险预警 - 用 'name' 字段查")
name = f"PROBE_R_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/risk-alert/configs/create", token, {"alertName": name, "alertType": "ORDER_ABNORMAL", "threshold": 100, "status": 1})
print(f"  create -> {p.get('data')}")
s, p = http("GET", f"{ADMIN_BASE}/risk-alert/configs?page=1&size=50", token)
items = p.get("data") if isinstance(p, dict) else []
if isinstance(items, dict):
    items = items.get("records") or items.get("list") or []
print(f"  list total={len(items)}, 包含 '{name}': {name in [it.get('name') for it in items]}")

# === B.7 库存调拨：用正确的字段名（reason）查 ===
print("\n" + "=" * 60)
print("B.7 库存调拨 - 用 'reason' 字段查")
reason = f"PROBE_T_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/inventory-transfer/create", token, {"fromWarehouseId": 1, "toWarehouseId": 2, "productId": 1, "quantity": 5, "reason": reason})
print(f"  create -> {p.get('data')}")
s, p = http("GET", f"{ADMIN_BASE}/inventory-transfer/list?page=1&size=50", token)
data = p.get("data") if isinstance(p, dict) else {}
items = data.get("list") or data.get("records") or [] if isinstance(data, dict) else []
print(f"  list total={len(items)}, 包含 reason '{reason}': {reason in [it.get('reason') for it in items]}")
print(f"  latest 3: {items[:3]}")
