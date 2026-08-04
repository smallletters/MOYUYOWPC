# -*- coding: utf-8 -*-
"""针对 biz=500/404/503 接口，用最小有效 body 重测，定位真实故障"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"


def http(method, url, token=None, body=None, timeout=10):
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
    except Exception as e:
        return 0, {"error": str(e)}


def login():
    s, p = http("POST", f"{ADMIN_BASE}/auth/login", body={"email": EMAIL, "password": PASSWORD})
    if s == 200 and p.get("code") == 0:
        return p["data"]["token"]
    return None


# 每个接口的最小有效 body（参考 admin.js 调用参数）
CASES = [
    ("POST", "/cms/create", {"title": "t", "type": "BANNER", "content": "c", "status": "DRAFT"}),
    ("PUT", "/cms/update", {"id": 1, "title": "t2", "type": "BANNER", "content": "c2", "status": "DRAFT"}),
    ("POST", "/coupons/create", {"name": "t_coupon", "type": "FIXED", "value": 10, "status": "ACTIVE"}),
    ("POST", "/products/batch", {"ids": [1, 2]}),
    ("POST", "/rbac/roles", {"name": "t_role", "code": "t_role_code", "description": "test"}),
    ("PUT", "/refunds/batch-approve", {"ids": [1]}),
    ("POST", "/review/batch-delete", {"ids": [1]}),
    ("POST", "/price/create", {"productId": 1, "price": 99.9, "currency": "USD"}),
    ("POST", "/products/sync-from-woo", {}),
    ("POST", "/products/push-all-to-woo", {}),
    ("POST", "/products/sync-stock-from-woo", {}),
]

token = login()
print(f"token={'OK' if token else 'NONE'}")
for method, path, body in CASES:
    s, p = http(method, f"{ADMIN_BASE}{path}", token=token, body=body)
    msg = p.get("message", str(p)[:120]) if isinstance(p, dict) else str(p)[:120]
    print(f"{method:4s} {path:35s} http={s} biz={p.get('code') if isinstance(p, dict) else '?'} msg={msg}")
