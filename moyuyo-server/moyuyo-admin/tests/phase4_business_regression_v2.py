# -*- coding: utf-8 -*-
"""阶段4 业务抽样 v2：修正 URL 编码 + 字段名错误后再次验证"""
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
            return e.code, {"_raw": txt[:200]}


def login():
    s, p = http("POST", LOGIN_URL, body={"email": "admin@moyuyo.com", "password": "123456"})
    return p["data"]["token"] if s == 200 and p.get("code") == 0 else None


def extract_list(payload):
    if not (isinstance(payload, dict) and payload.get("code") == 0):
        return []
    d = payload.get("data")
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        for k in ("records", "list", "items", "rows"):
            if isinstance(d.get(k), list):
                return d[k]
    return []


def is_ok(p):
    return isinstance(p, dict) and p.get("code") == 0


results = []


def case(name, fn):
    try:
        st, msg = fn()
    except Exception as e:
        st, msg = "EXCEPTION", repr(e)
    results.append((name, st, msg))
    print(f"[{st:4}] {name:50} | {msg}")


def test_sensitive():
    token = login()
    ts = int(time.time())
    name = f"PROBE_{ts}"  # 仅英文，避免 URL 编码问题
    s, p = http("POST", f"{ADMIN_BASE}/sensitive/create", token, {"word": name, "category": "其他", "status": "ENABLED"})
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/sensitive/list?keyword={urllib.parse.quote(name)}", token)
    items = extract_list(p2)
    found = any(it.get("word") == name for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_blacklist():
    token = login()
    ts = int(time.time())
    target = f"ph_{ts}"
    s, p = http("POST", f"{ADMIN_BASE}/blacklist/create", token, {"type": "user", "target": target, "reason": "v2"})
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/blacklist/list?page=1&size=20", token)
    data = p2.get("data") if isinstance(p2, dict) else None
    items = data.get("list", []) if isinstance(data, dict) else []
    found = any(it.get("value") == target for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_coupon_full():
    token = login()
    ts = int(time.time())
    name = f"V2C_{ts}"
    body = {"name": name, "type": "amount", "amount": 5, "totalQuantity": 50, "startTime": "2026-07-30T00:00:00", "endTime": "2027-12-31T23:59:59"}
    s, p = http("POST", f"{ADMIN_BASE}/coupons/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/coupons/list", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_flash_sale_full():
    token = login()
    ts = int(time.time())
    name = f"V2F_{ts}"
    body = {"name": name, "productId": 1, "flashPrice": 9.9, "originalPrice": 19.9, "stock": 10, "startTime": "2026-07-30T00:00:00", "endTime": "2027-12-31T23:59:59"}
    s, p = http("POST", f"{ADMIN_BASE}/flash-sales/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/flash-sales/list", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_points_activity_full():
    # 积分活动 list 是按 type 聚合（不是 name 匹配）；name 是 type 的固定映射
    # 验证：使用唯一 type 创建后，list 中应出现该 type
    token = login()
    ts = int(time.time())
    unique_type = "V2P_TYPE_" + str(ts)
    body = {"name": "V2P_" + str(ts), "type": unique_type, "points": 10, "dailyLimit": 1, "status": 1}
    s, p = http("POST", f"{ADMIN_BASE}/points/activities/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/points/activities", token)
    items = extract_list(p2)
    found = any(it.get("type") == unique_type for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中type={found} ({len(items)}条)"


def test_order_tag_full():
    token = login()
    ts = int(time.time())
    name = f"V2T_{ts}"
    s, p = http("POST", f"{ADMIN_BASE}/order-tags/create", token, {"name": name, "color": "#00ff00"})
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/order-tags/list", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_tariff_config_full():
    # 关税配置主键是 countryCode + productCategory，list 也按这两个查
    # 注意：country_code 字段是 VARCHAR(8)，超长会被 MySQL 拒（409 DataIntegrity）
    token = login()
    ts = int(time.time())
    country_code = ("V" + str(ts))[:8]  # 限长 8
    product_category = "V2CAT_" + str(ts)
    body = {"countryCode": country_code, "productCategory": product_category, "rate": 0.1}
    s, p = http("POST", f"{ADMIN_BASE}/tariff/configs/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/tariff/configs", token)
    items = extract_list(p2)
    found = any(it.get("countryCode") == country_code and it.get("productCategory") == product_category for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_risk_alert_config_full():
    # 风险预警 list 字段是 name/type（Service 已把 alertName/alertType 映射为 name/type）
    token = login()
    ts = int(time.time())
    name = "V2RISK_" + str(ts)
    body = {"alertName": name, "alertType": "ORDER_ABNORMAL", "threshold": 100, "status": 1}
    s, p = http("POST", f"{ADMIN_BASE}/risk-alert/configs/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/risk-alert/configs", token)
    items = extract_list(p2)
    found = any(it.get("name") == name for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def test_inventory_transfer_full():
    # 库存调拨字段是 reason（不是 remark）
    token = login()
    ts = int(time.time())
    reason_value = "V2转库测试_" + str(ts)
    body = {"fromWarehouseId": 1, "toWarehouseId": 2, "productId": 1, "quantity": 5, "reason": reason_value}
    s, p = http("POST", f"{ADMIN_BASE}/inventory-transfer/create", token, body)
    if s != 200 or not is_ok(p):
        return "FAIL", f"create {s}/{p}"
    s2, p2 = http("GET", f"{ADMIN_BASE}/inventory-transfer/list?page=1&size=20", token)
    data = p2.get("data") if isinstance(p2, dict) else None
    items = data.get("list", data.get("records", [])) if isinstance(data, dict) else []
    found = any(str(it.get("reason", "")).endswith(reason_value) for it in items)
    return ("OK" if found else "FAIL"), f"createOK, list命中={found} ({len(items)}条)"


def main():
    print(f"=== 阶段4 业务回归 v2 (修正 URL 编码 + 字段名) ===\n")
    case("A.1 敏感词 create+list (英文 keyword)", test_sensitive)
    case("A.2 黑名单 create+list (按 value 字段)", test_blacklist)
    case("B.1 优惠券 create+list", test_coupon_full)
    case("B.2 秒杀活动 create+list", test_flash_sale_full)
    case("B.3 积分活动 create+list", test_points_activity_full)
    case("B.4 订单标签 create+list", test_order_tag_full)
    case("B.5 关税配置 create+list", test_tariff_config_full)
    case("B.6 风险预警 create+list", test_risk_alert_config_full)
    case("B.7 库存调拨 create+list", test_inventory_transfer_full)
    fails = [r for r in results if r[1] not in ("OK",)]
    print(f"\n=== 阶段4 业务回归 v2: {len(results) - len(fails)} OK / {len(fails)} FAIL / {len(results)} TOTAL ===")
    if fails:
        print("失败列表:")
        for n, s, m in fails:
            print(f"  - {n}: {m}")


if __name__ == "__main__":
    main()
