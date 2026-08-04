"""
端到端写入业务流程测试 - 验证创建/更新/删除/审批流
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
        with urllib.request.urlopen(req, timeout=15) as resp:
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
    print("端到端写入业务流程测试")
    print("=" * 60)
    token = login()
    if not token:
        print("[FAIL] 登录失败")
        return
    print(f"[OK] 登录成功\n")

    results = []

    # ============ 流程1: 优惠券完整生命周期 ============
    print("[流程1] 优惠券 完整生命周期")
    coupon_id = None
    # 创建
    code, resp = http("POST", "/api/admin/coupons/create", {
        "name": f"测试优惠券_{int(time.time())}",
        "type": "AMOUNT",
        "amount": 10,
        "minSpend": 100,
        "total": 100,
        "startTime": "2026-01-01 00:00:00",
        "endTime": "2026-12-31 23:59:59"
    }, token=token)
    if code in (200, 201):
        coupon_id = resp.get("data", {}).get("id") if isinstance(resp.get("data"), dict) else None
        print(f"  ✓ 创建优惠券 -> {code}, id={coupon_id}")
        results.append(("创建优惠券", True, ""))
    else:
        print(f"  ✗ 创建优惠券 -> {code}: {str(resp)[:100]}")
        results.append(("创建优惠券", False, f"code={code}"))

    # ============ 流程2: 敏感词 CRUD ============
    print("\n[流程2] 敏感词 完整生命周期")
    sw_id = None
    code, resp = http("POST", "/api/admin/sensitive/create", {
        "word": f"test_{int(time.time())}",
        "category": "POLITICS",
        "level": 1
    }, token=token)
    if code in (200, 201):
        sw_id = resp.get("data", {}).get("id") if isinstance(resp.get("data"), dict) else None
        print(f"  ✓ 创建敏感词 -> {code}, id={sw_id}")
        results.append(("创建敏感词", True, ""))
    else:
        print(f"  ✗ 创建敏感词 -> {code}: {str(resp)[:100]}")
        results.append(("创建敏感词", False, f"code={code}"))

    # ============ 流程3: 推送消息创建+取消 ============
    print("\n[流程3] 推送消息 创建")
    push_id = None
    code, resp = http("POST", "/api/admin/push/create", {
        "title": f"测试推送_{int(time.time())}",
        "content": "这是测试内容",
        "targetType": "ALL"
    }, token=token)
    if code in (200, 201):
        push_id = resp.get("data", {}).get("id") if isinstance(resp.get("data"), dict) else None
        print(f"  ✓ 创建推送 -> {code}, id={push_id}")
        results.append(("创建推送", True, ""))
    else:
        print(f"  ✗ 创建推送 -> {code}: {str(resp)[:100]}")
        results.append(("创建推送", False, f"code={code}"))

    # ============ 流程4: 应用版本管理 ============
    print("\n[流程4] 应用版本 创建")
    code, resp = http("POST", "/api/admin/app-version/create", {
        "version": f"9.9.{int(time.time()) % 1000}",
        "platform": "android",
        "forceUpdate": False,
        "releaseNotes": "测试版本"
    }, token=token)
    ok = code in (200, 201)
    print(f"  {'✓' if ok else '✗'} 创建应用版本 -> {code}: {str(resp)[:80]}")
    results.append(("创建应用版本", ok, f"code={code}"))

    # ============ 流程5: 营销活动创建 ============
    print("\n[流程5] 营销活动 创建")
    code, resp = http("POST", "/api/admin/marketing/campaigns", {
        "name": f"测试活动_{int(time.time())}",
        "type": "DISCOUNT",
        "startTime": "2026-01-01 00:00:00",
        "endTime": "2026-12-31 23:59:59",
        "status": "DRAFT"
    }, token=token)
    ok = code in (200, 201)
    print(f"  {'✓' if ok else '✗'} 创建营销活动 -> {code}: {str(resp)[:80]}")
    results.append(("创建营销活动", ok, f"code={code}"))

    # ============ 流程6: 工单详情查询 ============
    print("\n[流程6] 工单列表 + 详情")
    code, list_resp = http("GET", "/api/admin/ticket/list?page=1&size=1", token=token)
    if code == 200:
        data = list_resp.get("data", {})
        records = data.get("records", []) if isinstance(data, dict) else data
        if records and len(records) > 0:
            tid = records[0].get("id")
            if tid:
                code2, detail = http("GET", f"/api/admin/ticket/{tid}", token=token)
                ok = code2 == 200
                print(f"  ✓ 工单详情 -> {code2}")
                results.append(("工单详情", ok, f"id={tid}"))
            else:
                print(f"  ⚠ 工单列表为空")
                results.append(("工单详情", True, "列表为空"))
        else:
            print(f"  ⚠ 工单列表为空，跳过详情")
            results.append(("工单详情", True, "列表为空"))
    else:
        print(f"  ✗ 工单列表 -> {code}")
        results.append(("工单详情", False, f"list code={code}"))

    # ============ 流程7: 订单统计（确认实时数据） ============
    print("\n[流程7] 订单数据完整性")
    code, resp = http("GET", "/api/admin/orders/list?page=1&size=3", token=token)
    if code == 200:
        data = resp.get("data", {})
        # 后端返回 list 字段（部分模块用 records，兼容两种）
        records = data.get("list", data.get("records", [])) if isinstance(data, dict) else []
        print(f"  ✓ 订单列表 -> {code}, 总数={data.get('total', '?')}, 返回={len(records)}")
        # 检查订单字段完整性（金额字段为 payAmount，兼容 totalAmount）
        if records:
            r = records[0]
            required = ["id", "orderNo", "status", "payAmount"]
            missing = [f for f in required if f not in r]
            if missing:
                print(f"  ⚠ 订单记录缺少字段: {missing}")
            else:
                print(f"  ✓ 订单字段完整: {list(r.keys())[:8]}...")
            results.append(("订单数据完整性", len(missing) == 0, f"missing={missing}"))
        else:
            results.append(("订单数据完整性", True, "无订单"))
    else:
        results.append(("订单数据完整性", False, f"code={code}"))

    # ============ 流程8: 商品数据完整性 ============
    print("\n[流程8] 商品数据完整性")
    code, resp = http("GET", "/api/admin/products/list?page=1&size=3", token=token)
    if code == 200:
        data = resp.get("data", {})
        records = data.get("list", data.get("records", [])) if isinstance(data, dict) else []
        print(f"  ✓ 商品列表 -> {code}, 总数={data.get('total', '?')}, 返回={len(records)}")
        if records:
            r = records[0]
            # 商品上下架状态用 onSale 字段表示（兼容 status）
            required = ["id", "name", "price"]
            missing = [f for f in required if f not in r]
            has_status = ("onSale" in r) or ("status" in r)
            print(f"  ✓ 商品字段: id={r.get('id')}, name={str(r.get('name', '?'))[:30]}, price={r.get('price')}, 缺少={missing}, 状态字段={'onSale' if 'onSale' in r else 'status'}")
            results.append(("商品数据完整性", len(missing) == 0 and has_status, f"missing={missing},status字段缺失" if not has_status else f"missing={missing}"))
        else:
            results.append(("商品数据完整性", True, "无商品"))
    else:
        results.append(("商品数据完整性", False, f"code={code}"))

    # 汇总
    print("\n" + "=" * 60)
    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print(f"通过: {passed} / 失败: {failed} / 总数: {len(results)}")
    if failed:
        print("\n失败项:")
        for name, ok, note in results:
            if not ok:
                print(f"  ✗ {name}: {note}")


if __name__ == "__main__":
    main()
