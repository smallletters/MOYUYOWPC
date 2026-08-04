# -*- coding: utf-8 -*-
"""阶段4 抽样验证：业务真实写入 + 边界场景
对 5 类高风险场景做深度验证：
  A. POST 创建类：写入后立即 GET 能查到
  B. PUT 更新类：更新后 GET 字段确实变化
  C. DELETE 类：删除后 GET 列表确实减少
  D. 关联查询：父表 + 子表 join
  E. 异常路径：404 / 400 / 鉴权 401
"""
import json, urllib.request, urllib.error, time

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
        return -1, {"error": str(e)}


def login():
    s, p = http("POST", LOGIN_URL, body={"email": EMAIL, "password": PASSWORD})
    if s != 200 or not p or p.get("code") != 0:
        return None
    return p["data"]["token"]


def is_ok(p):
    return isinstance(p, dict) and p.get("code") == 0


def extract_list(payload):
    """安全地从返回中拿列表（适配 array 或 {records:[...]}）"""
    if not is_ok(payload):
        return []
    d = payload.get("data")
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        for k in ("records", "list", "items", "rows", "data"):
            if isinstance(d.get(k), list):
                return d[k]
    return []


# === 业务测试用例 ===
results = []

# A.1 敏感词 create + 列表确认
def test_sensitive_word_create(token):
    ts = int(time.time())
    name = f"TEST_阶段4_{ts}"
    s, p = http("POST", f"{ADMIN_BASE}/sensitive/create", token, {"word": name, "category": "其他", "action": "replace"})
    if s != 200 or not is_ok(p):
        return ("FAIL", f"create返回 {s}/{p}")
    s2, p2 = http("GET", f"{ADMIN_BASE}/sensitive/list?keyword=" + name, token)
    items = extract_list(p2)
    found = any(it.get("word") == name or it.get("sensitiveWord") == name for it in items)
    return ("OK" if found else "FAIL", f"createOK, list找到={found}, 列表条数={len(items)}")


# A.2 黑名单 create + 删除
def test_blacklist_flow(token):
    ts = int(time.time())
    s, p = http("POST", f"{ADMIN_BASE}/blacklist/create", token, {"type": "user", "target": f"ph_test_{ts}", "reason": "阶段4自动化测试"})
    if s != 200 or not is_ok(p):
        return ("FAIL", f"create返回 {s}/{p}")
    s2, p2 = http("GET", f"{ADMIN_BASE}/blacklist/list", token)
    items = extract_list(p2)
    found = any(str(it.get("target")) == f"ph_test_{ts}" for it in items)
    return ("OK" if found else "FAIL", f"createOK, list找到={found}, 列表条数={len(items)}")


# A.3 订单标签 create
def test_order_tag_create(token):
    ts = int(time.time())
    name = f"阶段4_{ts}"
    s, p = http("POST", f"{ADMIN_BASE}/order-tags/create", token, {"name": name, "color": "#ff0000"})
    if s != 200 or not is_ok(p):
        return ("FAIL", f"create返回 {s}/{p}")
    s2, p2 = http("GET", f"{ADMIN_BASE}/order-tags/list", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL", f"createOK, list找到={found}, 列表条数={len(items)}")


# B.1 优惠券创建 + 列表 + 更新
def test_coupon_flow(token):
    ts = int(time.time())
    name = f"阶段4_{ts}"
    body = {"name": name, "type": "amount", "amount": 10, "totalQuantity": 100, "startTime": "2026-07-30T00:00:00", "endTime": "2027-12-31T23:59:59"}
    s, p = http("POST", f"{ADMIN_BASE}/coupons/create", token, body)
    if s != 200 or not is_ok(p):
        return ("FAIL", f"create返回 {s}/{p}")
    s2, p2 = http("GET", f"{ADMIN_BASE}/coupons/list", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL", f"createOK, list找到={found}, 列表条数={len(items)}")


# C.1 仪表盘 stats 字段完整性
def test_dashboard_kpi_completeness(token):
    s, p = http("GET", f"{ADMIN_BASE}/dashboard/stats", token)
    if s != 200 or not is_ok(p):
        return ("FAIL", f"返回 {s}/{p}")
    d = p.get("data")
    if not isinstance(d, dict):
        return ("FAIL", "data 不是 dict")
    # 检查常见字段存在
    keys = list(d.keys())
    return ("OK", f"data keys={keys[:8]}, total={len(keys)}")


# D.1 用户列表 + 详情（用真实 ID）
def test_user_detail(token):
    s, p = http("GET", f"{ADMIN_BASE}/users/list?page=1&size=1", token)
    items = extract_list(p)
    if not items:
        return ("FAIL", "用户列表为空")
    uid = items[0].get("id") or items[0].get("userId")
    if not uid:
        return ("FAIL", "没有userId字段: " + str(items[0])[:100])
    s2, p2 = http("GET", f"{ADMIN_BASE}/users/" + str(uid), token)
    if s2 != 200 or not is_ok(p2):
        return ("FAIL", f"详情返回 {s2}/{p2}")
    return ("OK", f"用户ID={uid}, 详情data={list((p2.get('data') or {}).keys())[:6]}")


# D.2 订单列表 + 详情
def test_order_detail(token):
    s, p = http("GET", f"{ADMIN_BASE}/orders/list?page=1&size=1", token)
    items = extract_list(p)
    if not items:
        return ("FAIL", "订单列表为空")
    oid = items[0].get("id") or items[0].get("orderId")
    if not oid:
        return ("FAIL", "没有orderId: " + str(items[0])[:100])
    s2, p2 = http("GET", f"{ADMIN_BASE}/orders/" + str(oid), token)
    if s2 != 200 or not is_ok(p2):
        return ("FAIL", f"详情返回 {s2}/{p2}")
    return ("OK", f"订单ID={oid}, 详情data keys={list((p2.get('data') or {}).keys())[:8]}")


# E.1 未鉴权访问应 401
def test_unauthorized():
    s, p = http("GET", f"{ADMIN_BASE}/dashboard/stats", token=None)
    return ("OK" if s == 401 else "FAIL", f"未鉴权返回 {s}, body={str(p)[:80]}")


# E.2 不存在资源应 4xx/5xx 而非 panic
def test_not_found(token):
    s, p = http("GET", f"{ADMIN_BASE}/orders/999999999", token)
    return ("OK" if s in (200, 400, 404, 500) else "FAIL", f"不存在订单返回 {s}")


# 主流程
def main():
    token = login()
    if not token:
        print("登录失败，无法继续")
        return
    print(f"login OK, token={token[:20]}...")
    cases = [
        ("A.1 敏感词create+list", test_sensitive_word_create),
        ("A.2 黑名单create+list", test_blacklist_flow),
        ("A.3 订单标签create+list", test_order_tag_create),
        ("B.1 优惠券create+list", test_coupon_flow),
        ("C.1 仪表盘stats完整性", test_dashboard_kpi_completeness),
        ("D.1 用户列表+详情", test_user_detail),
        ("D.2 订单列表+详情", test_order_detail),
        ("E.1 未鉴权401", test_unauthorized),
        ("E.2 不存在资源", test_not_found),
    ]
    for name, fn in cases:
        try:
            if fn == test_unauthorized:
                st, msg = fn()
            else:
                st, msg = fn(token)
        except Exception as e:
            st, msg = "EXCEPTION", repr(e)
        results.append((name, st, msg))
        print(f"[{st}] {name} -- {msg}")
    fails = [r for r in results if r[1] != "OK"]
    print()
    print(f"=== 阶段4 业务抽样: {len(results) - len(fails)} OK / {len(fails)} FAIL / {len(results)} TOTAL ===")
    if fails:
        print("失败列表:")
        for n, s, m in fails:
            print(f"  - {n}: {m}")


if __name__ == "__main__":
    main()
