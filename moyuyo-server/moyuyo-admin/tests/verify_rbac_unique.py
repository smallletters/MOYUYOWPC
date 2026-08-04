"""验证 rbac/roles 重复创建时返回友好提示（而不是通用 409）"""
import json
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"


def http(method, url, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


# 1. 登录
s, p = http("POST", f"{BASE}/api/admin/auth/login",
            body={"email": EMAIL, "password": PASSWORD})
if s != 200 or p.get("code") != 0:
    print("[FAIL] 登录失败", p)
    raise SystemExit(1)
token = p["data"]["token"]

# 2. 第一次创建角色（应成功 200）
name = f"verify_role_{int(time.time())}"
body = {"name": name, "code": f"VR_{int(time.time())}", "description": "verify"}
s1, p1 = http("POST", f"{BASE}/api/admin/rbac/roles", token=token, body=body)
print(f"[1] 首次创建  http={s1}  code={p1.get('code')}  msg={p1.get('message')}")
assert s1 == 200 and p1.get("code") == 0, f"首次创建失败: {p1}"

# 3. 第二次用相同 name 创建（应返回 400 + 具体错误）
s2, p2 = http("POST", f"{BASE}/api/admin/rbac/roles", token=token, body=body)
print(f"[2] 同名重创建  http={s2}  code={p2.get('code')}  msg={p2.get('message')}")
assert s2 == 200 and p2.get("code") == 400, f"应返回 400，实际 http={s2} code={p2.get('code')}"
assert "角色名称已存在" in p2.get("message", ""), \
    f"错误信息应包含'角色名称已存在'，实际: {p2.get('message')}"

# 4. 第三次用相同 code 不同 name 创建（应返回 400 + code 已存在）
body2 = {"name": f"another_{int(time.time())}", "code": body["code"], "description": "verify"}
s3, p3 = http("POST", f"{BASE}/api/admin/rbac/roles", token=token, body=body2)
print(f"[3] 同 code 创建  http={s3}  code={p3.get('code')}  msg={p3.get('message')}")
assert s3 == 200 and p3.get("code") == 400, f"应返回 400，实际 http={s3} code={p3.get('code')}"
assert "角色编码已存在" in p3.get("message", ""), \
    f"错误信息应包含'角色编码已存在'，实际: {p3.get('message')}"

print("\n[PASS] rbac/roles 重复名校验已生效，返回具体业务错误信息")
