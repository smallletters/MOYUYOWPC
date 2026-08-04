"""
深度调查创建操作409错误和商品状态字段
"""
import json
import urllib.request
import urllib.error
import urllib.parse

BASE = "http://localhost:8080"


def http(method, path, body=None, token=None):
    url = f"{BASE}{path}"
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            text = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(text)
            except json.JSONDecodeError:
                return resp.status, {"_raw": text[:300]}
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="ignore")
        try:
            return e.code, json.loads(text)
        except json.JSONDecodeError:
            return e.code, {"_raw": text[:300]}
    except Exception as e:
        return 0, {"error": str(e)}


def login():
    body = {"email": "admin@moyuyo.com", "password": "123456"}
    code, resp = http("POST", "/api/admin/auth/login", body)
    if code == 200 and resp.get("data", {}).get("token"):
        return resp["data"]["token"]
    return None


def main():
    token = login()
    if not token:
        print("登录失败")
        return

    print("=" * 70)
    print("1. 推送创建 - 详细测试")
    print("=" * 70)
    # 尝试不同的 payload
    payloads = [
        {"title": "测试", "content": "内容", "targetType": "ALL"},
        {"title": "测试", "content": "内容", "targetType": "ALL", "userIds": []},
        {"title": "测试", "content": "内容", "targetType": "ALL", "scheduledAt": "2026-12-01 00:00:00"},
    ]
    for p in payloads:
        code, resp = http("POST", "/api/admin/push/create", p, token=token)
        print(f"  payload={p}")
        print(f"  -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")

    print("\n" + "=" * 70)
    print("2. 应用版本创建 - 详细测试")
    print("=" * 70)
    # 查看一个已有应用版本的字段结构
    code, resp = http("GET", "/api/admin/app-version/list", token=token)
    if code == 200:
        data = resp.get("data", {})
        records = data.get("records", data) if isinstance(data, dict) else data
        if records:
            print(f"  现有版本样本: {json.dumps(records[0], ensure_ascii=False)[:300]}")

    # 尝试创建
    payloads = [
        {"version": "1.0.0", "platform": "android", "forceUpdate": False, "releaseNotes": "test"},
        {"version": "1.0.0", "platform": "android", "forceUpdate": False, "releaseNotes": "test", "downloadUrl": "https://example.com/app.apk"},
        {"version": "1.0.0", "platform": "android", "forceUpdate": False, "releaseNotes": "test", "downloadUrl": "https://example.com/app.apk", "minVersion": "1.0.0"},
    ]
    for p in payloads:
        code, resp = http("POST", "/api/admin/app-version/create", p, token=token)
        print(f"  payload={p}")
        print(f"  -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")

    print("\n" + "=" * 70)
    print("3. 营销活动创建 - 详细测试")
    print("=" * 70)
    # 查看一个已有活动的字段
    code, resp = http("GET", "/api/admin/marketing/campaigns", token=token)
    if code == 200:
        data = resp.get("data", {})
        records = data.get("records", data) if isinstance(data, dict) else data
        if records:
            print(f"  现有活动样本: {json.dumps(records[0], ensure_ascii=False)[:300]}")

    # 尝试创建
    payloads = [
        {"name": "测试活动", "type": "DISCOUNT", "startTime": "2026-01-01 00:00:00", "endTime": "2026-12-31 23:59:59", "status": "DRAFT"},
        {"name": "测试活动", "type": "DISCOUNT", "startTime": "2026-01-01T00:00:00", "endTime": "2026-12-31T23:59:59", "status": "DRAFT"},
    ]
    for p in payloads:
        code, resp = http("POST", "/api/admin/marketing/campaigns", p, token=token)
        print(f"  payload={p}")
        print(f"  -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")

    print("\n" + "=" * 70)
    print("4. 商品列表字段结构")
    print("=" * 70)
    code, resp = http("GET", "/api/admin/products/list?page=1&size=2", token=token)
    if code == 200:
        data = resp.get("data", {})
        records = data.get("records", [])
        if records:
            print(f"  第一个商品的所有字段:")
            print(json.dumps(records[0], ensure_ascii=False, indent=2))

    print("\n" + "=" * 70)
    print("5. 订单列表分页测试")
    print("=" * 70)
    # 测试不同的分页参数
    for page in [1, 2, 0]:
        for size in [3, 5, 10]:
            code, resp = http("GET", f"/api/admin/orders/list?page={page}&size={size}", token=token)
            if code == 200:
                data = resp.get("data", {})
                total = data.get("total", "?")
                records = data.get("records", [])
                print(f"  page={page}, size={size}: total={total}, returned={len(records)}")

    # 尝试不带分页参数
    code, resp = http("GET", "/api/admin/orders/list", token=token)
    if code == 200:
        data = resp.get("data", {})
        total = data.get("total", "?")
        records = data.get("records", [])
        print(f"  无参: total={total}, returned={len(records)}")


if __name__ == "__main__":
    main()
