"""
阶段4深度测试 - 验证所有管理后台关键按钮功能
"""
import json
import urllib.request
import urllib.error
import time
import sys

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


def get_list_id(path, token):
    """从列表接口获取一个ID用于后续测试"""
    code, resp = http("GET", path, token=token)
    if code == 200:
        data = resp.get("data", {})
        if isinstance(data, list) and data:
            return data[0].get("id")
        if isinstance(data, dict):
            for k in ["records", "list"]:
                if isinstance(data.get(k), list) and data[k]:
                    return data[k][0].get("id")
    return None


def main():
    token = login()
    if not token:
        print("登录失败")
        return

    print("=" * 70)
    print("阶段4深度按钮测试")
    print("=" * 70)

    results = []

    # 1. 推送创建
    print("\n[1] 推送创建 (title, content, channel)")
    code, resp = http("POST", "/api/admin/push/create",
        {"title": f"测试推送{int(time.time())}", "content": "内容", "channel": "NOTICE", "targetType": "ALL"},
        token=token)
    push_id = resp.get("data", {}).get("id") if code == 200 else None
    print(f"  -> {code} {'PASS' if code == 200 else 'FAIL'}")
    results.append(("push/create", code == 200))

    # 2. 推送发送（需要推送ID且状态为DRAFT）
    if push_id:
        print(f"\n[2] 推送发送 id={push_id}")
        code, resp = http("POST", f"/api/admin/push/{push_id}/send", token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("push/send", code == 200))

    # 3. 推送取消
    if push_id:
        print(f"\n[3] 推送取消 id={push_id}")
        code, resp = http("POST", f"/api/admin/push/{push_id}/cancel", token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("push/cancel", code == 200))

    # 4. 推送删除
    if push_id:
        print(f"\n[4] 推送删除 id={push_id}")
        code, resp = http("DELETE", f"/api/admin/push/{push_id}", token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("push/delete", code == 200))

    # 5. 应用版本创建（带正确字段）
    print("\n[5] 应用版本创建 (versionName, appType, versionCode)")
    code, resp = http("POST", "/api/admin/app-version/create",
        {"versionName": f"9.9.{int(time.time())}", "versionCode": 999, "appType": "ANDROID",
         "updateTitle": "测试", "updateDesc": "测试", "downloadUrl": "https://example.com/app.apk",
         "fileSize": "30MB", "forceUpdate": False},
        token=token)
    av_id = resp.get("data", {}).get("id") if code == 200 else None
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("app-version/create", code == 200))

    # 6. 应用版本更新
    if av_id:
        print(f"\n[6] 应用版本更新 id={av_id}")
        code, resp = http("PUT", "/api/admin/app-version/update",
            {"id": av_id, "updateTitle": "更新后的标题", "updateDesc": "更新后的说明"},
            token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("app-version/update", code == 200))

    # 7. 营销活动创建
    print("\n[7] 营销活动创建 (name, type, startDate, endDate ISO)")
    code, resp = http("POST", "/api/admin/marketing/campaigns",
        {"name": f"测试活动{int(time.time())}", "type": "DISCOUNT", "description": "desc",
         "startDate": "2026-01-01T00:00:00", "endDate": "2026-12-31T23:59:59"},
        token=token)
    cp_id = resp.get("data", {}).get("id") if code == 200 else None
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("marketing/campaigns.create", code == 200))

    # 8. 营销活动更新
    if cp_id:
        print(f"\n[8] 营销活动更新 id={cp_id}")
        code, resp = http("PUT", f"/api/admin/marketing/campaigns/{cp_id}",
            {"name": "更新活动", "type": "DISCOUNT", "description": "新desc",
             "startDate": "2026-02-01T00:00:00", "endDate": "2026-12-31T23:59:59"},
            token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("marketing/campaigns.update", code == 200))

    # 9. 营销活动删除
    if cp_id:
        print(f"\n[9] 营销活动删除 id={cp_id}")
        code, resp = http("DELETE", f"/api/admin/marketing/campaigns/{cp_id}", token=token)
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
        results.append(("marketing/campaigns.delete", code == 200))

    # 10. 优惠券创建
    print("\n[10] 优惠券创建")
    code, resp = http("POST", "/api/admin/coupons/create",
        {"name": f"测试券{int(time.time())}", "type": "AMOUNT", "amount": 10, "threshold": 100,
         "totalCount": 100, "startTime": "2026-01-01T00:00:00", "endTime": "2026-12-31T23:59:59"},
        token=token)
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("coupons/create", code == 200))

    # 11. 工单处理 - 取得工单ID
    ticket_id = get_list_id("/api/admin/tickets/list?page=1&size=1", token)
    if ticket_id:
        print(f"\n[11] 工单详情 id={ticket_id}")
        code, resp = http("GET", f"/api/admin/tickets/{ticket_id}", token=token)
        print(f"  -> {code}")
        results.append(("tickets/detail", code == 200))

    # 12. 用户列表
    print("\n[12] 用户列表")
    code, resp = http("GET", "/api/admin/users/list?page=1&size=5", token=token)
    if code == 200:
        data = resp.get("data", {})
        total = data.get("total", len(data.get("records", [])))
        print(f"  -> {code} total={total}")
    else:
        print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("users/list", code == 200))

    # 13. 仪表盘统计
    print("\n[13] 仪表盘统计")
    code, resp = http("GET", "/api/admin/dashboard/stats", token=token)
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("dashboard/stats", code == 200))

    # 14. 退款处理
    refund_id = get_list_id("/api/admin/refund/list?page=1&size=1", token)
    if refund_id:
        print(f"\n[14] 退款详情 id={refund_id}")
        code, resp = http("GET", f"/api/admin/refund/{refund_id}", token=token)
        print(f"  -> {code}")
        results.append(("refund/detail", code == 200))

    # 15. 风险预警列表（真实路径：/risk-alert/configs，见 admin.js getRiskAlertConfigs）
    print("\n[15] 风险预警列表")
    code, resp = http("GET", "/api/admin/risk-alert/configs?page=1&size=5", token=token)
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("risk-alert/configs", code == 200))

    # 16. 敏感词测试（真实路径：/sensitive/create，见 admin.js createSensitive）
    print("\n[16] 敏感词创建")
    code, resp = http("POST", "/api/admin/sensitive/create",
        {"word": f"test{int(time.time())}", "category": "POLITICS", "level": "HIGH"},
        token=token)
    print(f"  -> {code} {json.dumps(resp, ensure_ascii=False)[:200]}")
    results.append(("sensitive/create", code == 200))

    # 汇总
    print("\n" + "=" * 70)
    print("测试结果汇总")
    print("=" * 70)
    pass_count = sum(1 for _, ok in results if ok)
    fail_count = len(results) - pass_count
    for name, ok in results:
        print(f"  {'[PASS]' if ok else '[FAIL]'} {name}")
    print(f"\n总计: {len(results)}, 通过: {pass_count}, 失败: {fail_count}")


if __name__ == "__main__":
    main()
