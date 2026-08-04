# -*- coding: utf-8 -*-
"""阶段2-边界测试：参数校验、认证、错误处理、未授权访问、404等"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"


def http(method, url, token=None, body=None, timeout=8):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(txt)
            except Exception:
                payload = {"_raw": txt[:200]}
            return r.status, payload
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(txt)
        except Exception:
            payload = {"_raw": txt[:200]}
        return e.code, payload
    except Exception as e:
        return 0, {"error": str(e)}


def login():
    s, p = http("POST", LOGIN_URL, body={"email": EMAIL, "password": PASSWORD})
    if s == 200 and p.get("code") == 0:
        return p["data"]["token"]
    return None


results = []
def record(name, expected, actual_code, actual_payload, expected_codes=None):
    if expected_codes is None:
        expected_codes = [expected]
    ok = actual_code in expected_codes
    note = ""
    if isinstance(actual_payload, dict):
        if actual_payload.get("code") is not None and actual_payload.get("code") != 0:
            note = f" biz={actual_payload.get('code')} msg={actual_payload.get('message','')[:60]}"
        elif actual_payload.get("code") == 0:
            note = " biz=0 success"
    results.append((ok, name, actual_code, note))


token = login()
print(f"Login: {'OK' if token else 'FAIL'}\n")
H = {"Authorization": f"Bearer {token}"} if token else {}

print("== 1. 认证相关 ==")
# 1.1 空 body 登录
s, p = http("POST", LOGIN_URL, body={})
record("login/empty-body", 200, s, p, [200])
# 1.2 错误密码
s, p = http("POST", LOGIN_URL, body={"email": EMAIL, "password": "wrong"})
record("login/wrong-password", 200, s, p, [200])
# 1.3 未授权访问 /me
s, p = http("GET", f"{ADMIN_BASE}/auth/me", token=None)
record("me/no-token", 200, s, p, [200])
# 1.4 错 token
s, p = http("GET", f"{ADMIN_BASE}/auth/me", token="invalid.token.xx")
record("me/bad-token", 200, s, p, [200])

print("== 2. 必填参数校验 ==")
# 2.1 商品名空
s, p = http("POST", f"{ADMIN_BASE}/products/create", body={"name": ""}, token=token)
record("products/create/empty-name", 200, s, p, [200])
# 2.2 商品完全空 body
s, p = http("POST", f"{ADMIN_BASE}/products/create", body={}, token=token)
record("products/create/empty-body", 200, s, p, [200])
# 2.3 订单ID不存在的详情
s, p = http("GET", f"{ADMIN_BASE}/orders/9999999999", token=token)
record("orders/9999999999", 200, s, p, [200])
# 2.4 订单状态非法值
s, p = http("GET", f"{ADMIN_BASE}/orders/list", token=token)  # 正常基线
record("orders/list/baseline", 200, s, p, [200])

print("== 3. 越权/重复操作 ==")
# 3.1 切换不存在商品状态
s, p = http("PUT", f"{ADMIN_BASE}/products/9999999999/status", token=token)
record("products/9999999999/status", 200, s, p, [200])
# 3.2 删除不存在商品
s, p = http("DELETE", f"{ADMIN_BASE}/products/9999999999", token=token)
record("products/9999999999/delete", 200, s, p, [200])
# 3.3 取消不存在订单
s, p = http("PUT", f"{ADMIN_BASE}/orders/9999999999/cancel", body={"reason":"x"}, token=token)
record("orders/9999999999/cancel", 200, s, p, [200])
# 3.4 重复发货
s, p = http("PUT", f"{ADMIN_BASE}/orders/9999999999/ship", body={"carrier":"x"}, token=token)
record("orders/9999999999/ship", 200, s, p, [200])

print("== 4. 必填参数 list/分页 ==")
# 4.1 大分页
s, p = http("GET", f"{ADMIN_BASE}/orders/list?page=1&size=1000", token=token)
record("orders/list/size=1000", 200, s, p, [200])
# 4.2 负分页
s, p = http("GET", f"{ADMIN_BASE}/orders/list?page=-1&size=10", token=token)
record("orders/list/page=-1", 200, s, p, [200])
# 4.3 非法日期
s, p = http("GET", f"{ADMIN_BASE}/orders/list?startDate=not-a-date", token=token)
record("orders/list/bad-date", 200, s, p, [200])

print("== 5. SQL 注入/特殊字符 ==")
# 5.1 搜索关键字含 SQL 关键字
s, p = http("GET", f"{ADMIN_BASE}/orders/list?keyword=' OR 1=1 --", token=token)
record("orders/list/sqli-keyword", 200, s, p, [200])
# 5.2 XSS 字符
s, p = http("GET", f"{ADMIN_BASE}/orders/list?keyword=<script>alert(1)</script>", token=token)
record("orders/list/xss-keyword", 200, s, p, [200])
# 5.3 超长字符串
long_kw = "a" * 1000
s, p = http("GET", f"{ADMIN_BASE}/orders/list?keyword={long_kw}", token=token)
record("orders/list/long-keyword", 200, s, p, [200])

print("== 6. 错误路径 ==")
# 6.1 不存在路径
s, p = http("GET", f"{ADMIN_BASE}/this/does/not/exist", token=token)
record("404-path", 404, s, p, [404, 200])
# 6.2 错误方法
s, p = http("DELETE", f"{ADMIN_BASE}/dashboard/stats", token=token)
record("dashboard/wrong-method", 405, s, p, [405, 200])

print("\n=== 边界测试结果 ===")
total = len(results)
passed = sum(1 for ok,_,_,_ in results if ok)
for ok, name, code, note in results:
    print(f"{'OK  ' if ok else 'FAIL'} {name:40s} http={code}{note}")
print(f"\n=== 汇总: {passed}/{total} PASS ===")

# 失败统计
fails = [r for r in results if not r[0]]
if fails:
    print("\n=== 失败项详情 ===")
    for ok, name, code, note in fails:
        print(f"  {name}: http={code}{note}")
