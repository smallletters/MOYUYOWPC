"""
针对之前 500 错误的接口，提交有效参数验证业务功能恢复正常
"""
import json
import urllib.request
import urllib.error
import secrets
import time
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


def login():
    body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/admin/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))["data"]["token"]


def call(token, method, path, body=None):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    body_str = json.dumps(body) if body else ""
    if not any(path.startswith(p) for p in SKIP_SIGN_PATHS):
        headers.update(make_sign(method, path, body_str))
    data = body_str.encode("utf-8") if body_str else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", errors="replace")[:500]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")[:500]


def main():
    print("登录...")
    token = login()
    print(f"Token: {token[:20]}...\n")

    # 1) OrderTag 业务测试
    print("=" * 60)
    print("【OrderTag 业务测试】")
    print("=" * 60)
    body = {
        "name": "测试标签_" + str(int(time.time())),
        "color": "#FF5500",
        "description": "自动化测试创建",
        "sortOrder": 99,
        "enabled": 1,
    }
    code, resp = call(token, "POST", "/api/admin/order-tags/create", body)
    print(f"[{code}] POST /api/admin/order-tags/create")
    print(f"  Body: {json.dumps(body, ensure_ascii=False)}")
    print(f"  Response: {resp[:300]}")

    # 读取列表确认
    code2, resp2 = call(token, "GET", "/api/admin/order-tags?page=1&size=5")
    print(f"\n[{code2}] GET /api/admin/order-tags")
    print(f"  Response: {resp2[:300]}")

    # 2) InventoryTransfer 业务测试
    print("\n" + "=" * 60)
    print("【InventoryTransfer 业务测试】")
    print("=" * 60)
    code, resp = call(token, "GET", "/api/admin/inventory-transfer/list?page=1&size=5")
    print(f"[{code}] GET /api/admin/inventory-transfer/list")
    print(f"  Response: {resp[:300]}")

    # 3) OrderOps 业务测试
    print("\n" + "=" * 60)
    print("【OrderOps 业务测试】")
    print("=" * 60)
    code, resp = call(token, "GET", "/api/admin/order-ops/print/list?page=1&size=3")
    print(f"[{code}] GET /api/admin/order-ops/print/list")
    print(f"  Response: {resp[:300]}")

    code, resp = call(token, "GET", "/api/admin/order-ops/price-modify/list?page=1&size=3")
    print(f"\n[{code}] GET /api/admin/order-ops/price-modify/list")
    print(f"  Response: {resp[:300]}")

    code, resp = call(token, "GET", "/api/admin/order-ops/intercept/list?page=1&size=3")
    print(f"\n[{code}] GET /api/admin/order-ops/intercept/list")
    print(f"  Response: {resp[:300]}")

    # 4) 验证之前 500 错误现在能正确处理错误参数
    print("\n" + "=" * 60)
    print("【错误参数验证（之前 500，现在应该 400）】")
    print("=" * 60)
    code, resp = call(token, "POST", "/api/admin/order-ops/print/record", {"foo": "bar"})
    print(f"[{code}] POST /api/admin/order-ops/print/record (无效参数)")
    print(f"  Response: {resp[:200]}")

    code, resp = call(token, "POST", "/api/admin/order-ops/intercept/create", {"foo": "bar"})
    print(f"\n[{code}] POST /api/admin/order-ops/intercept/create (无效参数)")
    print(f"  Response: {resp[:200]}")


if __name__ == "__main__":
    main()
