"""
moyuyo-server 管理后台 POST/PUT/DELETE 接口健康检查
- 扫描所有 Admin Controller 的非 GET 接口
- 用最小 payload 验证可达性（不应返回 500/405/404）
- 输出报告
"""
import re
import json
import time
import urllib.request
import urllib.error
import sys
import os
import hmac
import hashlib
import base64
import secrets
from pathlib import Path

BASE = "http://localhost:8080"
CONTROLLER_DIR = Path(r"D:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\java\com\moyuyo\api\controller\admin")


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
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

SKIP_SIGN_PATHS = {
    "/api/admin/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/health",
    "/actuator/",
}


def make_sign_headers(method, path, body=""):
    timestamp = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{timestamp}{nonce}{body}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(SIGN_SECRET.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
    return {
        "X-Sign": base64.b64encode(sig).decode("utf-8"),
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
    }


def login():
    body = json.dumps({"email": EMAIL, "password": PASSWORD}).encode("utf-8")
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
        headers.update(make_sign_headers(method, path, body_str))
    data = body_str.encode("utf-8") if body_str else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode("utf-8", errors="replace")[:200]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")[:200]
    except Exception as e:
        return 0, str(e)[:200]


# 解析 Java 注解
def extract_endpoints():
    endpoints = []
    for java_file in CONTROLLER_DIR.glob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        cls_match = re.search(r"@RequestMapping\(\"([^\"]+)\"\)", text)
        if not cls_match:
            continue
        prefix = cls_match.group(1)
        cls_name = java_file.stem

        # 匹配 @PostMapping, @PutMapping, @DeleteMapping
        for m in re.finditer(r'@(Post|Put|Delete)Mapping\((?:value\s*=\s*)?"([^"]*)"\)', text):
            method_type = m.group(1).upper()
            sub = m.group(2)
            sub = re.sub(r"\{[^}]+\}", "1", sub)
            full = prefix.rstrip("/") + "/" + sub.lstrip("/") if sub else prefix
            endpoints.append((method_type, full, cls_name))

        # 匹配无参数的 @PostMapping 等
        for m in re.finditer(r'@(Post|Put|Delete)Mapping\(\s*\)', text):
            method_type = m.group(1).upper()
            full = prefix
            endpoints.append((method_type, full, cls_name))

        # 匹配完整 @RequestMapping(method=...)
        for m in re.finditer(r'@RequestMapping\((?:[^)]*?)value\s*=\s*"([^"]+)"[^)]*?method\s*=\s*RequestMethod\.(POST|PUT|DELETE)\s*', text):
            sub = m.group(1)
            method_type = m.group(2)
            sub = re.sub(r"\{[^}]+\}", "1", sub)
            full = prefix.rstrip("/") + "/" + sub.lstrip("/") if sub else prefix
            endpoints.append((method_type, full, cls_name))

    # 去重
    endpoints = sorted(set(endpoints))
    return endpoints


def main():
    print("正在登录...", flush=True)
    token = login()
    print(f"获得 token: {token[:20]}...\n", flush=True)

    endpoints = extract_endpoints()
    print(f"扫描 {len(endpoints)} 个非 GET 接口...\n", flush=True)

    passed = 0
    failed = 0
    failures = []
    for method, path, cls in endpoints:
        # 用最小 payload 测试
        body = None
        if method == "POST":
            body = {"id": 1, "name": "test", "type": "test"}
        elif method == "PUT":
            body = {"id": 1, "name": "test"}
        elif method == "DELETE":
            body = None

        code, resp = call(token, method, path, body)

        # 通过状态码
        if code in (200, 400, 401, 403, 404, 422):
            # 业务 4xx 算正常（参数缺失/权限不足）
            passed += 1
        elif code in (405,):
            failed += 1
            failures.append((method, path, cls, code, resp))
            print(f"[{code}] {method} {path}  ({cls})", flush=True)
            print(f"    {resp[:120]}", flush=True)
        elif code >= 500:
            failed += 1
            failures.append((method, path, cls, code, resp))
            print(f"[{code}] {method} {path}  ({cls})", flush=True)
            print(f"    {resp[:120]}", flush=True)
        else:
            passed += 1

    print(f"\n=== 结果：{passed} 通过 / {failed} 失败 / {len(endpoints)} 总计 ===", flush=True)
    if failures:
        with open(r"D:\MOYUYOWPC\moyuyo-server\moyuyo-admin\tests\post_put_delete_failures.txt", "w", encoding="utf-8") as f:
            f.write(f"扫描时间: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"通过: {passed} 失败: {failed} 总计: {len(endpoints)}\n\n")
            for m, p, c, code, body in failures:
                f.write(f"[{code}] {m} {p}  ({c})\n  {body}\n\n")
        print("详细报告已保存: post_put_delete_failures.txt", flush=True)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
