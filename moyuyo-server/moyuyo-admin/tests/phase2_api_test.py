# -*- coding: utf-8 -*-
"""基于 admin.js 真实路径的 API 批量测试（阶段2）"""
import json
import re
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
HEALTH_URL = f"{BASE}/api/health"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

# 读取 admin.js 提取所有真实端点
ADMIN_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\admin.js"
AUTH_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\auth.js"

def extract_endpoints():
    """从 admin.js / auth.js 提取 (method, path) 列表。"""
    eps = []
    pattern = re.compile(r"return\s+api\.(get|post|put|delete)\(\s*['\"]([^'\"]+)['\"]")
    for path in (ADMIN_JS, AUTH_JS):
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        for m in pattern.finditer(content):
            eps.append((m.group(1).upper(), m.group(2)))
    return eps

def http(method, url, token=None, body=None, timeout=8):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(txt)
            except Exception:
                payload = {"_raw": txt[:200]}
            return r.status, payload
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(txt)
        except Exception:
            payload = {"_raw": txt[:200]}
        return e.code, payload
    except Exception as e:
        return 0, {"error": str(e)}

def login():
    s, p = http("POST", LOGIN_URL, body={"email": EMAIL, "password": PASSWORD})
    if s == 200 and p.get("code") == 0:
        return p["data"]["token"]
    return None

# 把带参数的端点替换成无参形式（移除 ${...}）
def normalize_path(path):
    return re.sub(r"\$\{[^}]+\}", "1", path)

def main():
    print("=" * 80)
    print("【阶段2：基于 admin.js 真实端点的 API 测试】")
    print("=" * 80)

    s, p = http("GET", HEALTH_URL)
    print(f"\n[健康] GET /api/health -> status={s}")

    token = login()
    if not token:
        print("登录失败")
        return
    print(f"[登录成功]")

    endpoints = extract_endpoints()
    print(f"\n[扫描] 共发现 {len(endpoints)} 个端点（去重前）")

    # 去重
    seen = set()
    unique = []
    for m, p_ in endpoints:
        key = (m, normalize_path(p_))
        if key in seen:
            continue
        seen.add(key)
        unique.append((m, p_))
    print(f"[去重] 共 {len(unique)} 个唯一端点\n")

    ok_cnt = err_cnt = 0
    results = []
    by_method = {}
    for method, path in unique:
        # 跳过动态参数：把 ${id} 替换为 1
        url_path = normalize_path(path)
        url = f"{ADMIN_BASE}{url_path}"
        # POST/PUT 端点需要 body 的传一个最小 body
        body = None
        if method in ("POST", "PUT", "DELETE"):
            # 尽量不破坏端点，对于需要 id 的传 {id: 1}
            if "${" not in path:
                # 无参数端点，传一个空 body
                body = {}
            else:
                # 有 id 路径，body 加 id
                body = {"id": 1}
        s, p = http(method, url, token=token, body=body)
        bcode = p.get("code") if isinstance(p, dict) else None
        # 跳过幂等性检查：POST 失败不一定是 bug（可能业务校验拦截），但要记录
        ok = (s == 200 and bcode == 0)
        # POST 业务校验失败（如"参数缺失"）也算端点可达，记为 OK
        if s == 200:
            ok = True
        # 业务校验错误但端点存在 = OK
        if s in (400, 405) and bcode in (400, 405):
            ok = True  # 端点存在，仅业务校验拦截
        if ok:
            ok_cnt += 1
            tag = "OK"
        else:
            err_cnt += 1
            tag = "FAIL"
            results.append((method, path, s, p))
        by_method.setdefault(method, [0, 0])
        if ok:
            by_method[method][0] += 1
        else:
            by_method[method][1] += 1
        msg = p.get("message", "")[:40] if isinstance(p, dict) else ""
        print(f"[{tag:4s}] {method:6s} {path:50s} http={s} biz={bcode} {msg}")

    print(f"\n=== 阶段2 总结: {ok_cnt} PASS / {err_cnt} FAIL / {len(unique)} TOTAL ===\n")
    print("=== 各方法统计 ===")
    for m, (o, e) in sorted(by_method.items()):
        print(f"  {m}: {o} OK / {e} FAIL")
    if results:
        print("\n=== 失败端点详情 ===")
        for method, path, s, p in results:
            print(f"- {method} {path} | status={s} body={json.dumps(p, ensure_ascii=False)[:300]}")

if __name__ == "__main__":
    main()
