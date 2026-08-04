"""阶段4验证 - 敏感词、风险告警、优惠券"""
import json
import urllib.request
import urllib.error
import time

BASE = "http://localhost:8080"

def http(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


def main():
    code, resp = http("POST", "/api/admin/auth/login", {"email": "admin@moyuyo.com", "password": "123456"})
    token = resp["data"]["token"]
    print("登录:", code)

    # 1. 敏感词
    print("\n===== 敏感词 =====")
    code, resp = http("POST", "/api/admin/sensitive/create",
        {"word": f"test_{int(time.time())}", "category": "POLITICS", "status": "ACTIVE"},
        token=token)
    print(f"  create -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")
    sw_id = resp.get("data", {}).get("id") if code == 200 else None

    if sw_id:
        code, resp = http("GET", "/api/admin/sensitive/list", token=token)
        print(f"  list -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")
        code, resp = http("DELETE", f"/api/admin/sensitive/{sw_id}", token=token)
        print(f"  delete -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")

    # 2. 风险告警
    print("\n===== 风险告警 =====")
    code, resp = http("GET", "/api/admin/risk-alert/configs", token=token)
    print(f"  configs -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")
    code, resp = http("GET", "/api/admin/risk-alert/history?page=1&size=2", token=token)
    print(f"  history -> {code}: {json.dumps(resp, ensure_ascii=False)[:200]}")

    # 3. 优惠券 (带startTime/endTime)
    print("\n===== 优惠券 (带 startTime/endTime) =====")
    payload = {"name": f"测试券{int(time.time())}", "type": "fixed", "value": 10, "minAmount": 100,
               "totalCount": 100, "startTime": "2026-01-01T00:00:00", "endTime": "2026-12-31T23:59:59",
               "active": True}
    code, resp = http("POST", "/api/admin/coupons/create", payload, token=token)
    print(f"  create -> {code}: {json.dumps(resp, ensure_ascii=False)[:400]}")


if __name__ == "__main__":
    main()
