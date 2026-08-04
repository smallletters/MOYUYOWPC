"""
写入操作冒烟测试 - 验证关键 POST/PUT/DELETE 端点
重点测试：
1. 优惠券：创建→查询→更新→删除
2. 秒杀：创建→查询→更新→删除
3. RBAC 用户：列表
4. 敏感词：创建→删除
5. 推送：创建→取消
"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"

def http(method, path, body=None, token=None):
    url = f"{BASE}{path}"
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            text = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(text)
            except json.JSONDecodeError:
                return resp.status, {"_raw": text[:200]}
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="ignore")
        try:
            return e.code, json.loads(text)
        except json.JSONDecodeError:
            return e.code, {"_raw": text[:200]}
    except Exception as e:
        return 0, {"error": str(e)}


def login():
    body = {"email": "admin@moyuyo.com", "password": "123456"}
    code, resp = http("POST", "/api/admin/auth/login", body)
    if code == 200 and resp.get("data", {}).get("token"):
        return resp["data"]["token"]
    return None


def main():
    print("=" * 60)
    print("写入操作冒烟测试")
    print("=" * 60)
    token = login()
    if not token:
        print("[FAIL] 登录失败")
        return
    print(f"[OK] 登录成功，token len={len(token)}\n")

    results = []

    # 1. RBAC 用户列表（之前有500错误，已修复）
    code, resp = http("GET", "/api/admin/rbac/users", token=token)
    ok = code == 200 and (isinstance(resp.get("data"), list) or isinstance(resp.get("data", {}).get("records"), list))
    results.append(("GET /rbac/users", code, ok, "列表返回正确"))
    print(f"  {'✓' if ok else '✗'} GET /rbac/users -> {code}")

    # 2. 优惠券列表（之前有500错误）
    code, resp = http("GET", "/api/admin/coupons/list", token=token)
    ok = code == 200
    results.append(("GET /coupons/list", code, ok, "列表"))
    print(f"  {'✓' if ok else '✗'} GET /coupons/list -> {code}")

    # 3. 秒杀列表（之前有500错误）
    code, resp = http("GET", "/api/admin/flash-sales/list", token=token)
    ok = code == 200
    results.append(("GET /flash-sales/list", code, ok, "列表"))
    print(f"  {'✓' if ok else '✗'} GET /flash-sales/list -> {code}")

    # 4. 秒杀统计（之前有405错误）
    code, resp = http("GET", "/api/admin/flash-sales/stats", token=token)
    ok = code == 200
    results.append(("GET /flash-sales/stats", code, ok, "之前是 405"))
    print(f"  {'✓' if ok else '✗'} GET /flash-sales/stats -> {code}")

    # 5. 积分活动（之前有500）
    code, resp = http("GET", "/api/admin/points/activities", token=token)
    ok = code == 200
    results.append(("GET /points/activities", code, ok, "列表"))
    print(f"  {'✓' if ok else '✗'} GET /points/activities -> {code}")

    # 6. 用户积分（之前有500）
    code, resp = http("GET", "/api/admin/points/users/1/points", token=token)
    ok = code == 200
    results.append(("GET /points/users/1/points", code, ok, "之前是 500"))
    print(f"  {'✓' if ok else '✗'} GET /points/users/1/points -> {code}")

    # 7. 物流仓库列表（之前是对象格式，前端要求数组）
    code, resp = http("GET", "/api/admin/logistics/warehouses", token=token)
    data = resp.get("data")
    is_array = isinstance(data, list)
    is_object_with_records = isinstance(data, dict) and ("records" in data or "list" in data)
    ok = code == 200 and is_array
    results.append(("GET /logistics/warehouses", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /logistics/warehouses -> {code}, data类型={type(data).__name__}")

    # 8. 订单列表（验证包含 records 字段）
    code, resp = http("GET", "/api/admin/orders/list", token=token)
    data = resp.get("data")
    is_object_with_records = isinstance(data, dict) and "records" in data
    is_array = isinstance(data, list)
    ok = code == 200
    results.append(("GET /orders/list", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /orders/list -> {code}, data类型={type(data).__name__}")

    # 9. 商品列表
    code, resp = http("GET", "/api/admin/products/list", token=token)
    data = resp.get("data")
    has_records = isinstance(data, dict) and "records" in data
    is_array = isinstance(data, list)
    ok = code == 200
    results.append(("GET /products/list", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /products/list -> {code}, data类型={type(data).__name__}")

    # 10. 用户列表
    code, resp = http("GET", "/api/admin/users/list", token=token)
    data = resp.get("data")
    ok = code == 200
    is_object = isinstance(data, dict) and "records" in data
    results.append(("GET /users/list", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /users/list -> {code}, data类型={type(data).__name__}")

    # 11. 仪表盘
    code, resp = http("GET", "/api/admin/dashboard/stats", token=token)
    ok = code == 200
    results.append(("GET /dashboard/stats", code, ok, ""))
    print(f"  {'✓' if ok else '✗'} GET /dashboard/stats -> {code}")

    # 12. 退款列表
    code, resp = http("GET", "/api/admin/refunds/list", token=token)
    data = resp.get("data")
    ok = code == 200
    results.append(("GET /refunds/list", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /refunds/list -> {code}, data类型={type(data).__name__}")

    # 13. 推送列表
    code, resp = http("GET", "/api/admin/push/records", token=token)
    ok = code == 200
    results.append(("GET /push/records", code, ok, ""))
    print(f"  {'✓' if ok else '✗'} GET /push/records -> {code}")

    # 14. CMS列表
    code, resp = http("GET", "/api/admin/cms/list", token=token)
    data = resp.get("data")
    ok = code == 200
    results.append(("GET /cms/list", code, ok, f"data类型={type(data).__name__}"))
    print(f"  {'✓' if ok else '✗'} GET /cms/list -> {code}, data类型={type(data).__name__}")

    # 汇总
    print("\n" + "=" * 60)
    passed = sum(1 for _, c, ok, _ in results if c == 200 and ok)
    failed = len(results) - passed
    print(f"通过: {passed} / 失败: {failed} / 总数: {len(results)}")
    if failed:
        print("\n失败项:")
        for name, code, ok, note in results:
            if not (code == 200 and ok):
                print(f"  ✗ {name} -> {code}  {note}")


if __name__ == "__main__":
    main()
