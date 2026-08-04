# -*- coding: utf-8 -*-
"""扫描后端所有 admin Controller 接口路径，并批量实测（阶段1-接口清单生成）"""
import json
import os
import re
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

CTRL_DIR = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\java\com\moyuyo\api\controller\admin"


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


def extract_controller_endpoints():
    """从 admin 包下所有 Controller 提取 (class, method, path)"""
    eps = []
    for fname in sorted(os.listdir(CTRL_DIR)):
        if not fname.endswith(".java"):
            continue
        fpath = os.path.join(CTRL_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()
        # 剥离块注释与行注释，避免匹配到 javadoc 中的示例注解
        content = re.sub(r"/\*.*?\*/", "", content, flags=re.DOTALL)
        content = re.sub(r"//.*", "", content)
        # 类级 @RequestMapping 前缀
        class_prefix = ""
        m = re.search(r'@RequestMapping\s*\(\s*value\s*=\s*"([^"]+)"', content)
        if not m:
            m = re.search(r'@RequestMapping\s*\("([^"]+)"\)', content)
        if m:
            class_prefix = m.group(1)
        # 方法级映射
        method_map = {
            "GETMAPPING": "GET",
            "POSTMAPPING": "POST",
            "PUTMAPPING": "PUT",
            "DELETEMAPPING": "DELETE",
        }
        for mm in re.finditer(
            r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?',
            content,
        ):
            raw_method = mm.group(1).upper()
            method = method_map.get(raw_method)
            path = mm.group(2) or ""
            if method is None:
                # 仅统计具体 method 属性的，跳过纯类级
                continue
            full = (class_prefix.rstrip("/") + "/" + path.lstrip("/")).rstrip("/") or "/"
            eps.append((fname.replace(".java", ""), method, full))
    return eps


def main():
    token = login()
    print(f"token={'OK' if token else 'NONE'}")
    eps = extract_controller_endpoints()
    print(f"提取到 {len(eps)} 个接口")
    results = []
    for cls, method, path in eps:
        # 类级映射已含 /api/admin 前缀时直接拼 BASE，否则拼 ADMIN_BASE
        if path.startswith("/api/"):
            url = f"{BASE}{path}"
        else:
            url = f"{ADMIN_BASE}{path}"
        if method == "GET":
            s, p = http("GET", url, token=token)
            biz = p.get("code", "n/a") if isinstance(p, dict) else "n/a"
            ok = (s == 200) and (biz in (0, "0"))
            results.append({"class": cls, "method": method, "path": path, "http": s, "biz": biz, "ok": ok})
        else:
            # 非 GET：发送空 body 探测（业务校验失败不算接口故障）
            s, p = http(method, url, token=token, body={})
            biz = p.get("code", "n/a") if isinstance(p, dict) else "n/a"
            ok = (s == 200)
            results.append({"class": cls, "method": method, "path": path, "http": s, "biz": biz, "ok": ok})

    ok_cnt = sum(1 for r in results if r["ok"])
    print(f"\n=== 接口状态: {ok_cnt} / {len(results)} 可达（HTTP 200） ===")
    fails = [r for r in results if not r["ok"]]
    if fails:
        print("\n=== 非200接口 ===")
        for r in fails:
            print(f"  {r['method']:6s} {r['path']:60s} http={r['http']} biz={r['biz']}")

    with open(r"D:\MOYUYOWPC\api-inventory.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n结果已写入 api-inventory.json")


if __name__ == "__main__":
    main()
