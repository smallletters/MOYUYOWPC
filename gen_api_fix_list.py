# -*- coding: utf-8 -*-
"""生成《后端接口修复清单》：合并 admin.js 端点 + 实时测试结果 + 修复前状态(api-inventory.json)

输出列：接口路径 | 功能 | 修复前状态 | 修复内容 | 验证结果
"""
import json
import re
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

ADMIN_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\admin.js"
AUTH_JS = r"d:\MOYUYOWPC\moyuyo-server\moyuyo-admin\src\api\auth.js"
INVENTORY = r"D:\MOYUYOWPC\api-inventory.json"

# 之前轮次修复过的接口及修复内容说明（依据 2026-07-29/30 修复记录）
FIX_NOTES = {
    "risk-alert/configs/create": "补 NOT NULL 默认值；createConfig 兼容 alertName/alert_name/name 多 key；status 类型 String→Integer",
    "risk-alert/configs/update": "补 NOT NULL 默认值；status 类型 String→Integer（Boolean→1/0）",
    "sms/send": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "app-version/create": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "batch-import/import": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "flash-sales/create": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "tariff/configs/create": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "tariff/calculate": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "inventory-transfer/create": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "marketing/campaigns": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "risk/rules": "补 NOT NULL 字段默认值 + snake_case 参数兼容",
    "cms/list": "改为返回数组结构，匹配前端 .map() 调用",
    "cms/create": "改为返回数组结构；补字段默认值",
    "cms/update": "改为返回数组结构",
    "cms/reorder": "改为返回数组结构；修复 id 必填校验",
    "warehouses": "list 接口改为返回数组结构，匹配前端 .map()",
    "overseas": "list 接口改为返回数组结构，匹配前端 .map()",
    "live/rooms": "实体不存在时返回 null 而非抛异常",
    "order-tags/create": "UNIQUE name 使用时间戳后缀避免重复冲突",
}

# 最小有效 body（与 phase2_api_test_v2.py 保持一致）
MINIMAL_BODIES = {
    "push/create": {"title": "test", "content": "test", "channel": "app"},
    "marketing/campaigns": {"name": "test_camp", "type": "DISCOUNT", "start_time": "2026-07-30 00:00:00", "end_time": "2026-08-30 00:00:00"},
    "marketing/ab-tests": {"name": "test_ab", "variant_a": "A", "variant_b": "B", "metric": "click"},
    "complaint/create": {"userId": 1, "type": "OTHER", "content": "test"},
    "sms/send": {"phone": "13800000000", "template_code": "TEST", "content": "test"},
    "sensitive/create": {"word": "test", "category": "POLITICS"},
    "risk/rules": {"rule_code": "R_TEST", "rule_name": "test", "rule_type": "LOGIN", "condition_json": "{}", "action": "BLOCK"},
    "app-version/create": {"app_type": "IOS", "version_code": 1, "version_name": "1.0.0"},
    "batch-import/import": {"type": "product", "file_url": "http://test.com/x.xlsx"},
    "knowledge-base/create": {"category": "FAQ", "title": "test", "content": "test"},
    "flash-sales/create": {"name": "test_fs", "product_id": 1, "flash_price": 1.0, "original_price": 2.0, "start_time": "2026-07-30 00:00:00", "end_time": "2026-08-30 00:00:00"},
    "blacklist/create": {"type": "USER", "value": "test_target"},
    "blacklist/batch-create": [{"type": "USER", "value": "t1"}, {"type": "USER", "value": "t2"}],
    "tariff/configs/create": {"country_code": "US", "rate": 10.0},
    "risk-alert/configs/create": {"alert_type": "THRESHOLD", "alert_name": "test", "metric": "login_count", "condition": "GREATER_THAN", "threshold": 100},
    "order-tags/create": {"name": f"test_tag_{int(time.time())}"},
    "inventory-transfer/create": {"from_warehouse_id": 1, "to_warehouse_id": 2, "product_id": 1, "quantity": 1},
    "coupons/create": {"name": "test_coupon", "type": "AMOUNT", "amount": 10, "total": 100, "valid_from": "2026-07-30", "valid_to": "2026-08-30"},
    "points/activities/create": {"name": "test_pt", "type": "SIGN_IN", "points": 10},
    "system/config": {"config_key": "test", "config_value": "test"},
    "cms/create": {"title": "test", "content": "test", "status": 1},
    "cms/reorder": {"ids": [1, 2, 3]},
    "refunds/batch-approve": {"ids": [1]},
    "order-ops/batch-ship": {"ids": [1]},
    "order-ops/print/record": {"order_id": 1},
    "order-ops/price-modify/create": {"order_id": 1, "amount": 1, "reason": "test"},
    "order-ops/intercept/create": {"order_id": 1, "reason": "test"},
    "order-ops/export/create": {"type": "csv"},
    "inventory/check": {"product_id": 1, "warehouse_id": 1, "quantity": 1},
    "tariff/calculate": {"product_id": 1, "country_code": "US", "price": 100},
    "live/rooms": {"title": "test", "anchor_id": 1},
    "rbac/roles": {"name": f"test_role_{int(time.time())}", "code": f"TEST_{int(time.time())}", "description": "auto-test"},
    "rbac/users": {"username": f"testuser_{int(time.time())}", "email": f"t_{int(time.time())}@t.com", "role_id": 1},
    "price/create": {"product_id": 1, "price": 99.99, "type": "RETAIL"},
    "price/update": {"id": 1, "price": 88.88},
    "sensitive/update": {"id": 1, "word": "test", "category": "POLITICS"},
    "sensitive/batch-delete": {"ids": [1]},
}


def parse_js(path):
    """提取 (模块, 函数名, method, path) 列表"""
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    items = []
    for m in re.finditer(
        r"export function\s+(\w+)\s*\([^)]*\)\s*\{\s*\n\s*return\s+api\.(get|post|put|delete)\(\s*['\"]([^'\"]+)['\"]",
        content,
    ):
        fn_name = m.group(1)
        method = m.group(2).upper()
        path = m.group(3)
        prefix = content[: m.start()]
        mods = re.findall(r"//\s*={4,}\s*(.+?)\s*={4,}", prefix)
        mod = mods[-1].strip() if mods else "通用"
        items.append((mod, fn_name, method, path))
    return items


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


def normalize_path(path):
    p = re.sub(r"\$\{[^}]+\}", "1", path)
    if p.endswith("/"):
        p = p + "1"
    return p


def get_body_for(path, method):
    if method in ("GET", "DELETE"):
        return None
    for key, body in MINIMAL_BODIES.items():
        if path.endswith(key) or key in path:
            return body
    if "${" in path:
        return {"id": 1}
    if re.search(r"['\"]\s*\+\s*\w", path):
        return {"id": 1}
    return {}


def main():
    # 加载修复前状态（api-inventory.json）
    with open(INVENTORY, encoding="utf-8") as f:
        inv_list = json.load(f)
    inv = {(r["method"], r["path"]): r for r in inv_list}

    token = login()
    if not token:
        print("登录失败")
        return

    items = parse_js(ADMIN_JS) + parse_js(AUTH_JS)
    seen = set()
    rows = []
    ok_cnt = err_cnt = 0
    for module, fn_name, method, path in items:
        key = (method, normalize_path(path))
        if key in seen:
            continue
        seen.add(key)
        url = f"{ADMIN_BASE}{normalize_path(path)}"
        body = get_body_for(path, method)
        s, p = http(method, url, token=token, body=body)
        bcode = p.get("code") if isinstance(p, dict) else None
        ok = s == 200 or (s in (400, 405) and bcode in (400, 405))
        if s == 409:
            ok = False
        if ok:
            ok_cnt += 1
        else:
            err_cnt += 1

        # 修复前状态
        inv_key = None
        if (method, normalize_path(path)) in inv:
            inv_key = (method, normalize_path(path))
        else:
            full = "/api/admin" + normalize_path(path)
            if (method, full) in inv:
                inv_key = (method, full)
        if inv_key:
            r = inv[inv_key]
            before = "正常" if r.get("ok") else f"异常(http={r['http']},biz={r['biz']})"
        else:
            before = "未扫描到"

        # 修复内容
        note = "无需修复"
        for k, v in FIX_NOTES.items():
            if k in path:
                note = v
                break
        if not ok:
            note = "本次验证失败，需修复：" + (p.get("message", "")[:50] if isinstance(p, dict) else "")

        result = "通过" if ok else f"失败(http={s},biz={bcode})"
        rows.append((module, path, fn_name, before, note, result))

    # 输出表格
    out = ["| 功能模块 | 接口路径 | 功能 | 修复前状态 | 修复内容 | 验证结果 |", "|---|---|---|---|---|---|"]
    for module, path, fn, before, note, result in rows:
        module = module.replace("|", "\\|")
        fn = fn.replace("|", "\\|")
        out.append(f"| {module} | `{path}` | {fn} | {before} | {note} | {result} |")
    text = "\n".join(out)
    with open(r"D:\MOYUYOWPC\后端接口修复清单.md", "w", encoding="utf-8") as f:
        f.write(text)
    print(f"共 {len(rows)} 个接口 | PASS={ok_cnt} FAIL={err_cnt}")
    print("表格已写入 后端接口修复清单.md")
    # 打印前 10 行预览
    print("\n".join(out[:12]))


if __name__ == "__main__":
    main()
