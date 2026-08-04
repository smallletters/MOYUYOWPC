"""检查 mo_coupon 表的列定义"""
import json
import urllib.request

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

    # 查所有敏感词，看 CouponEntity 实际写入的字段
    code, resp = http("GET", "/api/admin/coupons/list?page=1&size=2", token=token)
    print("coupons/list:", code)
    if code == 200:
        data = resp.get("data", {})
        print(json.dumps(data, ensure_ascii=False, indent=2)[:600])

    # 查敏感词类型
    code, resp = http("GET", "/api/admin/sensitive/categories", token=token)
    print("\nsensitive/categories:", code, json.dumps(resp, ensure_ascii=False)[:300])


if __name__ == "__main__":
    main()
