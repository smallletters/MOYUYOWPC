# -*- coding: utf-8 -*-
"""针对 B.3-B.7 真实写入失败做精确探查"""
import json, urllib.request, urllib.error, time, urllib.parse

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


def is_ok(p):
    return isinstance(p, dict) and p.get("code") == 0


def list_records(path, token):
    s, p = http("GET", f"{ADMIN_BASE}{path}", token)
    if not is_ok(p):
        return None, p
    d = p.get("data")
    if isinstance(d, list):
        return d, None
    if isinstance(d, dict):
        for k in ("records", "list", "items", "rows"):
            if isinstance(d.get(k), list):
                return d[k], None
    return [], None


token = login()
ts = int(time.time())

# === B.3 积分活动 ===
print("=" * 60)
print("B.3 积分活动 create+list 探查")
name = f"PROBE_P_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/points/activities/create", token, {"name": name, "type": "SIGN_IN", "points": 10, "dailyLimit": 1, "status": 1})
print(f"  create: status={s}, code={p.get('code')}, data={p.get('data')}")
print(f"  full response: {p}")
items, err = list_records("/points/activities?page=1&size=50", token)
print(f"  list: items={len(items) if items else 0}, err={err}")
if items:
    print(f"  latest 3: {items[-3:]}")
    names = [it.get('name') for it in items]
    print(f"  contains '{name}': {name in names}")

# === B.5 关税配置 ===
print("\n" + "=" * 60)
print("B.5 关税配置 create+list 探查")
code = f"PROBE_TARIFF_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/tariff/configs/create", token, {"code": code, "name": "V2探查", "rate": 0.1, "country": "CN"})
print(f"  create: status={s}, code={p.get('code')}, data={p.get('data')}")
print(f"  full response: {p}")
items, err = list_records("/tariff/configs?page=1&size=50", token)
print(f"  list: items={len(items) if items else 0}, err={err}")
if items:
    print(f"  latest 3: {items[-3:]}")
    codes = [it.get('code') for it in items]
    print(f"  contains '{code}': {code in codes}")

# === B.6 风险预警 ===
print("\n" + "=" * 60)
print("B.6 风险预警 create+list 探查")
name = f"PROBE_RISK_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/risk-alert/configs/create", token, {"alertName": name, "alertType": "ORDER_ABNORMAL", "threshold": 100, "status": 1})
print(f"  create: status={s}, code={p.get('code')}, data={p.get('data')}")
print(f"  full response: {p}")
items, err = list_records("/risk-alert/configs?page=1&size=50", token)
print(f"  list: items={len(items) if items else 0}, err={err}")
if items:
    print(f"  latest 3: {items[-3:]}")
    names = [it.get('alertName') or it.get('name') for it in items]
    print(f"  alertNames: {names}")
    print(f"  contains '{name}': {name in names}")

# === B.7 库存调拨 ===
print("\n" + "=" * 60)
print("B.7 库存调拨 create+list 探查")
remark = f"PROBE_TRANSFER_{ts}"
s, p = http("POST", f"{ADMIN_BASE}/inventory-transfer/create", token, {"fromWarehouseId": 1, "toWarehouseId": 2, "productId": 1, "quantity": 5, "remark": remark})
print(f"  create: status={s}, code={p.get('code')}, data={p.get('data')}")
print(f"  full response: {p}")
items, err = list_records("/inventory-transfer/list?page=1&size=50", token)
print(f"  list: items={len(items) if items else 0}, err={err}")
if items:
    print(f"  latest 3: {items[-3:]}")
    remarks = [it.get('remark') for it in items]
    print(f"  remarks: {remarks}")
    print(f"  contains '{remark}': {remark in remarks}")
