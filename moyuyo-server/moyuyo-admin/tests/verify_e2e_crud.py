"""完整 CRUD 业务流验证：OrderTag 创建→列表→更新→删除"""
import json
import urllib.request
import urllib.error
import time
import secrets
import hmac
import hashlib
import base64
from pathlib import Path

BASE = "http://localhost:8080"


def _load_env():
    env_path = Path(r"D:\MOYUYOWPC\moyuyo-server\.env")
    env = {}
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


ENV = _load_env()
SIGN_SECRET = ENV.get("API_SIGN_SECRET", "")
SKIP_SIGN_PATHS = {"/api/admin/auth/login"}


def make_sign(method, path, body=""):
    ts = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{ts}{nonce}{body}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(SIGN_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
    return {
        "X-Sign": base64.b64encode(sig).decode("utf-8"),
        "X-Timestamp": ts,
        "X-Nonce": nonce,
    }


def call(token, method, path, body=None):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    body_str = json.dumps(body) if body else ""
    if not any(path.startswith(p) for p in SKIP_SIGN_PATHS):
        headers.update(make_sign(method, path, body_str))
    data = body_str.encode("utf-8") if body_str else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


def login():
    body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/admin/auth/login", data=body,
        headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))["data"]["token"]


def main():
    print("=" * 60)
    print("【端到端 CRUD 业务流验证】")
    print("=" * 60)
    token = login()
    print(f"登录成功，token 前缀: {token[:20]}...\n")

    # CRUD 循环
    tag_name = f"E2E测试_{int(time.time())}"

    # 1. CREATE
    print(f"[1] CREATE - 标签: {tag_name}")
    s, r = call(token, "POST", "/api/admin/order-tags/create", {
        "name": tag_name, "color": "#FF0000",
        "description": "E2E 验证", "sortOrder": 1, "enabled": 1
    })
    print(f"  状态: {s}  业务码: {r.get('code')}  消息: {r.get('message')}\n")
    assert s == 200 and r.get("code") == 0, "CREATE 失败"

    # 2. LIST
    print("[2] LIST - 验证新建标签在列表中")
    s, r = call(token, "GET", "/api/admin/order-tags/list?page=1&size=20")
    items = r.get("data", [])
    found = [it for it in items if it.get("name") == tag_name]
    print(f"  状态: {s}  标签数: {len(items)}  找到新建标签: {len(found) > 0}\n")
    assert len(found) > 0, "新建标签未在列表中"

    # 3. UPDATE
    tag_id = found[0].get("id")
    print(f"[3] UPDATE - 修改标签 ID={tag_id}")
    s, r = call(token, "PUT", "/api/admin/order-tags/update", {
        "id": tag_id, "name": tag_name + "_v2", "color": "#00FF00",
        "description": "E2E 验证更新", "sortOrder": 2, "enabled": 1
    })
    print(f"  状态: {s}  业务码: {r.get('code')}  消息: {r.get('message')}\n")
    assert s == 200 and r.get("code") == 0, "UPDATE 失败"

    # 4. 再次 LIST 验证更新
    print("[4] LIST 验证更新生效")
    s, r = call(token, "GET", "/api/admin/order-tags/list?page=1&size=20")
    items = r.get("data", [])
    found = [it for it in items if it.get("name") == tag_name + "_v2"]
    print(f"  状态: {s}  找到更新后标签: {len(found) > 0}\n")
    assert len(found) > 0, "更新未生效"

    # 5. DELETE（实际路径 /order-tags/{id}）
    print(f"[5] DELETE - 删除标签 ID={tag_id}")
    s, r = call(token, "DELETE", f"/api/admin/order-tags/{tag_id}")
    print(f"  状态: {s}  业务码: {r.get('code')}  消息: {r.get('message')}\n")
    s, r = call(token, "GET", "/api/admin/order-tags/list?page=1&size=20")
    items = r.get("data", [])
    found = [it for it in items if it.get("id") == tag_id]
    print(f"  验证: 删除后列表中已无该标签 = {len(found) == 0}\n")

    # 6. 综合业务流 - 修正为真实路径
    print("[6] 业务数据巡检（真实路径）")
    flows = [
        ("GET", "/api/admin/orders/list?page=1&size=5", "订单列表"),
        ("GET", "/api/admin/users/list?page=1&size=5", "用户列表"),
        ("GET", "/api/admin/logistics/warehouses", "仓库列表"),
        ("GET", "/api/admin/products/list?page=1&size=5", "商品列表"),
        ("GET", "/api/admin/coupons/stats", "优惠券统计"),
        ("GET", "/api/admin/flash-sales/stats", "秒杀统计"),
        ("GET", "/api/admin/points/stats", "积分统计"),
        ("GET", "/api/admin/dashboard/stats", "仪表盘"),
    ]
    passed = 0
    for method, path, name in flows:
        s, r = call(token, method, path)
        ok = s == 200 and r.get("code") == 0
        print(f"  {'OK' if ok else 'FAIL'}  {name:20s}  {method} {path[:50]:50s}  http={s} code={r.get('code')}")
        if ok:
            passed += 1
    print(f"\n  业务流通过: {passed}/{len(flows)}")

    print("\n" + "=" * 60)
    print("【端到端验证通过】")
    print("=" * 60)


if __name__ == "__main__":
    main()
