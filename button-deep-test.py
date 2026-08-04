"""
阶段3+4 综合按钮级测试
- 后端按钮对应写操作
- 前端页面加载
"""
import json
import time
import urllib.request
import urllib.error
from pathlib import Path

BASE = "http://localhost:8080"
PAGES_BASE = "http://127.0.0.1:5173/admin"
REPORT = Path(r"D:\MOYUYOWPC\button-deep-result.json")


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
            return resp.status, json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="ignore")
        try:
            return e.code, json.loads(text)
        except Exception:
            return e.code, {"_raw": text[:200]}
    except Exception as e:
        return 0, {"error": str(e)}


def login():
    code, resp = http("POST", "/api/admin/auth/login", {"email": "admin@moyuyo.com", "password": "123456"})
    if code == 200:
        return resp.get("data", {}).get("token")
    return None


def main():
    token = login()
    if not token:
        print("登录失败")
        return 1
    print(f"Token 获取成功，长度: {len(token)}")

    # 各模块核心按钮操作
    cases = []

    # ============ 1. 推送管理 ============
    print("\n[1] 推送模块: 创建->发送->取消->删除")
    code, resp = http("POST", "/api/admin/push/create",
        {"title": f"测试推送{int(time.time())}", "content": "内容", "channel": "NOTICE", "targetType": "ALL"},
        token=token)
    push_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("push/create", code, code == 200))
    if push_id:
        code, _ = http("POST", f"/api/admin/push/{push_id}/send", token=token)
        cases.append(("push/send", code, code == 200))
        code, _ = http("POST", f"/api/admin/push/{push_id}/cancel", token=token)
        cases.append(("push/cancel", code, code == 200))
        code, _ = http("DELETE", f"/api/admin/push/{push_id}", token=token)
        cases.append(("push/delete", code, code == 200))

    # ============ 2. App 版本管理 ============
    print("\n[2] App版本: 创建->更新")
    code, resp = http("POST", "/api/admin/app-version/create",
        {"versionName": f"9.9.{int(time.time())%1000}", "versionCode": 999, "appType": "ANDROID",
         "updateTitle": "测试", "updateDesc": "测试", "downloadUrl": "https://example.com/a.apk",
         "fileSize": "30MB", "forceUpdate": False}, token=token)
    av_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("app-version/create", code, code == 200))
    if av_id:
        code, _ = http("PUT", "/api/admin/app-version/update",
            {"id": av_id, "updateTitle": "更新后", "updateDesc": "更新后说明"}, token=token)
        cases.append(("app-version/update", code, code == 200))

    # ============ 3. 营销活动 ============
    print("\n[3] 营销活动: 创建->更新->删除")
    code, resp = http("POST", "/api/admin/marketing/campaigns",
        {"name": f"测试活动{int(time.time())}", "type": "DISCOUNT", "description": "desc",
         "startDate": "2026-01-01T00:00:00", "endDate": "2026-12-31T23:59:59"}, token=token)
    cp_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("marketing/campaigns.create", code, code == 200))
    if cp_id:
        code, _ = http("PUT", f"/api/admin/marketing/campaigns/{cp_id}",
            {"name": "更新活动", "type": "DISCOUNT", "description": "新desc",
             "startDate": "2026-02-01T00:00:00", "endDate": "2026-12-31T23:59:59"}, token=token)
        cases.append(("marketing/campaigns.update", code, code == 200))
        code, _ = http("DELETE", f"/api/admin/marketing/campaigns/{cp_id}", token=token)
        cases.append(("marketing/campaigns.delete", code, code == 200))

    # ============ 4. 优惠券 ============
    print("\n[4] 优惠券: 创建")
    code, resp = http("POST", "/api/admin/coupons/create",
        {"name": f"测试券{int(time.time())}", "type": "AMOUNT", "amount": 10, "threshold": 100,
         "totalCount": 100, "startTime": "2026-01-01T00:00:00", "endTime": "2026-12-31T23:59:59"}, token=token)
    cases.append(("coupons/create", code, code == 200))

    # ============ 5. RBAC 角色 ============
    print("\n[5] RBAC: 角色创建")
    role_id = None
    code, resp = http("POST", "/api/admin/rbac/roles",
        {"name": f"测试角色{int(time.time())}", "description": "测试", "status": "ACTIVE"}, token=token)
    if code == 200 and isinstance(resp, dict):
        data = resp.get("data") or {}
        if isinstance(data, dict):
            role_id = data.get("id")
    cases.append(("rbac/roles.create", code, code == 200 and role_id is not None))
    if role_id:
        code, _ = http("PUT", f"/api/admin/rbac/roles/{role_id}",
            {"name": "更新角色", "description": "更新后", "status": "ACTIVE"}, token=token)
        cases.append(("rbac/roles.update", code, code == 200))
        code, _ = http("DELETE", f"/api/admin/rbac/roles/{role_id}", token=token)
        cases.append(("rbac/roles.delete", code, code == 200))

    # ============ 6. CMS 内容 ============
    print("\n[6] CMS: 内容创建->更新->删除")
    code, resp = http("POST", "/api/admin/cms/create",
        {"title": f"测试内容{int(time.time())}", "content": "正文", "type": "ARTICLE", "status": "DRAFT"},
        token=token)
    cms_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("cms/create", code, code == 200))
    if cms_id:
        code, _ = http("PUT", "/api/admin/cms/update",
            {"id": cms_id, "title": "更新标题", "content": "更新正文", "type": "ARTICLE", "status": "DRAFT"},
            token=token)
        cases.append(("cms/update", code, code == 200))
        code, _ = http("DELETE", f"/api/admin/cms/{cms_id}", token=token)
        cases.append(("cms/delete", code, code == 200))

    # ============ 7. 敏感词 ============
    print("\n[7] 敏感词: 创建->删除")
    code, resp = http("POST", "/api/admin/sensitive/create",
        {"word": f"test_{int(time.time())}", "category": "POLITICS", "status": "ACTIVE"}, token=token)
    sw_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("sensitive/create", code, code == 200))
    if sw_id:
        code, _ = http("DELETE", f"/api/admin/sensitive/{sw_id}", token=token)
        cases.append(("sensitive/delete", code, code == 200))

    # ============ 8. 仓库 ============
    print("\n[8] 仓库: 创建->更新->删除")
    code, resp = http("POST", "/api/admin/logistics/warehouses",
        {"name": f"测试仓库{int(time.time())}", "type": "DOMESTIC", "city": "上海", "address": "测试",
         "area": 100, "manager": "测试", "status": "ACTIVE"}, token=token)
    wh_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("warehouses/create", code, code == 200))
    if wh_id:
        code, _ = http("PUT", f"/api/admin/logistics/warehouses/{wh_id}",
            {"name": "更新仓库", "type": "DOMESTIC", "city": "上海", "address": "更新后",
             "area": 200, "manager": "测试2", "status": "ACTIVE"}, token=token)
        cases.append(("warehouses/update", code, code == 200))
        code, _ = http("DELETE", f"/api/admin/logistics/warehouses/{wh_id}", token=token)
        cases.append(("warehouses/delete", code, code == 200))

    # ============ 9. 承运商 ============
    print("\n[9] 承运商: 创建->删除")
    code, resp = http("POST", "/api/admin/logistics/carriers",
        {"name": f"测试承运商{int(time.time())}", "transportMode": "AIR", "avgDeliveryDays": 3.5,
         "firstWeightPrice": 50, "renewWeightPrice": 20, "praiseRate": 95.5, "status": "ACTIVE"}, token=token)
    cr_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("carriers/create", code, code == 200))
    if cr_id:
        code, _ = http("DELETE", f"/api/admin/logistics/carriers/{cr_id}", token=token)
        cases.append(("carriers/delete", code, code == 200))

    # ============ 10. 黑名单 ============
    print("\n[10] 黑名单: 创建->删除")
    code, resp = http("POST", "/api/admin/blacklist/create",
        {"type": "USER", "value": f"test_{int(time.time())}", "reason": "测试"}, token=token)
    bl_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("blacklist/create", code, code == 200 and bl_id is not None))
    if bl_id:
        code, _ = http("DELETE", f"/api/admin/blacklist/{bl_id}", token=token)
        cases.append(("blacklist/delete", code, code == 200))

    # ============ 11. 风控规则 ============
    print("\n[11] 风控规则: 创建->删除")
    # 用毫秒级时间戳确保 ruleCode 全局唯一
    code, resp = http("POST", "/api/admin/risk/rules",
        {"ruleCode": f"T_{int(time.time()*1000)}", "ruleName": f"测试规则{int(time.time())}",
         "ruleType": "ORDER", "conditionJson": "{\"threshold\": 10000}",
         "action": "BLOCK", "priority": 5, "enabled": True, "description": "测试规则"}, token=token)
    rr_id = resp.get("data", {}).get("id") if code == 200 else None
    cases.append(("risk/rules.create", code, code == 200 and rr_id is not None))
    if rr_id:
        code, _ = http("DELETE", f"/api/admin/risk/rules/{rr_id}", token=token)
        cases.append(("risk/rules.delete", code, code == 200))

    # ============ 12. 退款审批 ============
    print("\n[12] 退款: 列表+详情")
    code, resp = http("GET", "/api/admin/refunds/list?page=1&size=1", token=token)
    if code == 200 and resp.get("data", {}).get("records"):
        rid = resp["data"]["records"][0].get("id")
        code2, _ = http("GET", f"/api/admin/refunds/{rid}", token=token)
        cases.append(("refunds/list", code, code == 200))
        cases.append(("refunds/detail", code2, code2 == 200))

    # 汇总
    print("\n" + "=" * 70)
    print("按钮级测试结果")
    print("=" * 70)
    pass_count = sum(1 for _, _, ok in cases if ok)
    fail_count = len(cases) - pass_count
    for name, code, ok in cases:
        flag = "PASS" if ok else "FAIL"
        print(f"  [{flag}] {name:<35} {code}")
    print(f"\n总计: {len(cases)}, 通过: {pass_count}, 失败: {fail_count}")

    REPORT.write_text(json.dumps({
        "scan_time": time.strftime("%Y-%m-%d %H:%M:%S"),
        "total": len(cases), "pass": pass_count, "fail": fail_count,
        "results": [{"name": n, "code": c, "ok": ok} for n, c, ok in cases]
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告: {REPORT}")
    return 0 if fail_count == 0 else 1


if __name__ == "__main__":
    import sys
    sys.exit(main())
