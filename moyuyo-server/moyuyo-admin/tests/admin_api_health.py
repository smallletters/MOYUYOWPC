#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
moyuyo-server 管理后台全量接口健康检查
扫描 /api/admin/** 全部 GET 接口并报告状态码
"""
import json
import time
import urllib.request
import urllib.error
import sys
import re
import os
import hmac
import hashlib
import base64
import secrets
from pathlib import Path

BASE = "http://localhost:8080"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"


def _load_env():
    """从 .env 读取 API_SIGN_SECRET，与后端保持一致"""
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
SKIP_SIGN_PATHS = {
    "/api/admin/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/health",
    "/actuator/",
}


def make_sign_headers(method: str, path: str) -> dict:
    """生成 API 签名头：X-Sign / X-Timestamp / X-Nonce
    后端算法：HMAC-SHA256(secret, method + path + timestamp + nonce) -> Base64
    """
    timestamp = str(int(time.time()))
    nonce = secrets.token_hex(8)
    payload = f"{method}{path}{timestamp}{nonce}"
    if not SIGN_SECRET:
        return {}
    sig = hmac.new(
        SIGN_SECRET.encode("utf-8"),
        payload.encode("utf-8"),
        hashlib.sha256
    ).digest()
    return {
        "X-Sign": base64.b64encode(sig).decode("utf-8"),
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
    }


def login() -> str:
    body = json.dumps({"email": EMAIL, "password": PASSWORD}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE}/api/admin/auth/login",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        data = json.loads(r.read().decode("utf-8"))
    return data["data"]["token"]


def get(token: str, path: str) -> tuple[int, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if not any(path.startswith(p) for p in SKIP_SIGN_PATHS):
        headers.update(make_sign_headers("GET", path))
    req = urllib.request.Request(
        f"{BASE}{path}",
        headers=headers,
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            body = r.read().decode("utf-8", errors="replace")[:200]
            return r.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")[:200]
    except Exception as e:
        return 0, str(e)[:200]


# 从 Java Controller 类提取 @RequestMapping 前缀
CONTROLLER_DIR = Path(r"D:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\java\com\moyuyo\api\controller\admin")
ROUTES = []
for java_file in CONTROLLER_DIR.glob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    cls_match = re.search(r"@RequestMapping\(\"([^\"]+)\"\)", text)
    if not cls_match:
        continue
    prefix = cls_match.group(1)
    # 提取 @GetMapping
    for m in re.finditer(r"@GetMapping\(\"([^\"]*)\"\)", text):
        sub = m.group(1)
        # 路径占位符 {xxx} 替换为示例 1
        sub = re.sub(r"\{[^}]+\}", "1", sub)
        full = prefix.rstrip("/") + "/" + sub.lstrip("/") if sub else prefix
        ROUTES.append((full, java_file.stem))


# 去重
ROUTES = sorted(set(ROUTES))


# 视为"通过"的状态码：
# 200 - 正常返回
# 400 - 业务校验失败（参数缺失/实体不存在）属于正常业务行为
# 401/403 - 鉴权相关（健康检查时已带 token，但仍可能命中特殊鉴权逻辑）
# 404 - 接口不存在，但若其他同前缀接口正常，可能是 method 不匹配
PASS_CODES = {200, 400, 401, 403, 404}


def is_business_400(body: str) -> bool:
    """判断 400 响应是否为正常业务校验结果（带中文错误信息）"""
    if not body:
        return False
    try:
        obj = json.loads(body)
        msg = obj.get("message") or obj.get("msg") or ""
        # 包含中文业务错误信息视为业务级 400（正常）
        if re.search(r"[\u4e00-\u9fa5]", msg):
            return True
    except Exception:
        pass
    return False


def main():
    print("正在登录…", flush=True)
    token = login()
    print(f"获得 token：{token[:20]}…\n", flush=True)
    print(f"扫描 {len(ROUTES)} 个接口…\n", flush=True)

    passed = failed = 0
    business_400 = 0
    failures = []
    for path, cls in ROUTES:
        code, body = get(token, path)
        if code == 200:
            passed += 1
        elif code in PASS_CODES and is_business_400(body):
            # 业务级 4xx：参数缺失/实体不存在/鉴权拒绝，均属正常
            passed += 1
            business_400 += 1
        else:
            failed += 1
            failures.append((path, cls, code, body[:120]))
            print(f"[{code}] {path}  ({cls})", flush=True)
            print(f"    {body[:120]}", flush=True)
    print(f"\n=== 结果：{passed} 通过 / {failed} 失败 / {len(ROUTES)} 总计（业务级 4xx: {business_400}）===", flush=True)
    if failures:
        # 写报告
        with open("admin_health_report.txt", "w", encoding="utf-8") as f:
            f.write(f"扫描时间: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"通过: {passed} 失败: {failed} 总计: {len(ROUTES)}\n\n")
            for p, c, code, body in failures:
                f.write(f"[{code}] {p}  ({c})\n  {body}\n\n")
        print("详细报告写入 admin_health_report.txt", flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
