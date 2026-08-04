# -*- coding: utf-8 -*-
"""基于 admin.js 真实路径的 API 批量测试（阶段2-改进版）

改进点：
1. 对 create 类端点发送最小有效 body，避免 NOT NULL 约束误报 409
2. 处理 admin.js 中字符串拼接的路径模式（如 '/path/' + id）
3. 对 batch-create 类端点发送 List body
4. 增加业务校验失败 vs 真实失败的区分
"""
import json
import re
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
ADMIN_BASE = f"{BASE}/api/admin"
HEALTH_URL = f"{BASE}/api/health"
LOGIN_URL = f"{ADMIN_BASE}/auth/login"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

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


# 各端点最小有效 body 配置（key 为 endpoint path 的关键子串）
MINIMAL_BODIES = {
    # 推送
    "push/create": {"title": "test", "content": "test", "channel": "app"},
    # 营销活动（mo_marketing_campaign 字段）
    "marketing/campaigns": {"name": "test_camp", "type": "DISCOUNT", "start_time": "2026-07-30 00:00:00", "end_time": "2026-08-30 00:00:00"},
    "marketing/ab-tests": {"name": "test_ab", "variant_a": "A", "variant_b": "B", "metric": "click"},
    # 投诉
    "complaint/create": {"userId": 1, "type": "OTHER", "content": "test"},
    # 短信（mo_sms_record 字段）
    "sms/send": {"phone": "13800000000", "template_code": "TEST", "content": "test"},
    # 敏感词
    "sensitive/create": {"word": "test", "category": "POLITICS"},
    # 风控规则（mo_risk_rule NOT NULL: rule_code/rule_name/rule_type/condition_json/action）
    "risk/rules": {"rule_code": "R_TEST", "rule_name": "test", "rule_type": "LOGIN", "condition_json": "{}", "action": "BLOCK"},
    # 应用版本（mo_app_version NOT NULL: app_type/version_code/version_name）
    "app-version/create": {"app_type": "IOS", "version_code": 1, "version_name": "1.0.0"},
    # 批量导入
    "batch-import/import": {"type": "product", "file_url": "http://test.com/x.xlsx"},
    # 知识库（mo_knowledge_base NOT NULL: category/title/content）
    "knowledge-base/create": {"category": "FAQ", "title": "test", "content": "test"},
    # 限时秒杀（mo_flash_sale NOT NULL: name/product_id/flash_price/original_price/start_time/end_time）
    "flash-sales/create": {"name": "test_fs", "product_id": 1, "flash_price": 1.0, "original_price": 2.0, "start_time": "2026-07-30 00:00:00", "end_time": "2026-08-30 00:00:00"},
    # 黑名单（mo_blacklist NOT NULL: type/value，status 有默认）
    "blacklist/create": {"type": "USER", "value": "test_target"},
    "blacklist/batch-create": [{"type": "USER", "value": "t1"}, {"type": "USER", "value": "t2"}],
    # 关税（mo_tariff_config NOT NULL: country_code/rate）
    "tariff/configs/create": {"country_code": "US", "rate": 10.0},
    # 风控告警（mo_risk_alert_config NOT NULL: alert_type/alert_name/metric/condition/threshold）
    "risk-alert/configs/create": {"alert_type": "THRESHOLD", "alert_name": "test", "metric": "login_count", "condition": "GREATER_THAN", "threshold": 100},
    # 订单标签（mo_order_tag NOT NULL: name，UNIQUE）
    "order-tags/create": {"name": f"test_tag_{int(time.time())}"},
    # 库存调拨
    "inventory-transfer/create": {"from_warehouse_id": 1, "to_warehouse_id": 2, "product_id": 1, "quantity": 1},
    # 优惠券
    "coupons/create": {"name": "test_coupon", "type": "AMOUNT", "amount": 10, "total": 100, "valid_from": "2026-07-30", "valid_to": "2026-08-30"},
    # 积分
    "points/activities/create": {"name": "test_pt", "type": "SIGN_IN", "points": 10},
    # 系统配置
    "system/config": {"config_key": "test", "config_value": "test"},
    # CMS
    "cms/create": {"title": "test", "content": "test", "status": 1},
    "cms/reorder": {"ids": [1, 2, 3]},
    # 退款
    "refunds/batch-approve": {"ids": [1]},
    # 订单操作
    "order-ops/batch-ship": {"ids": [1]},
    "order-ops/print/record": {"order_id": 1},
    "order-ops/price-modify/create": {"order_id": 1, "amount": 1, "reason": "test"},
    "order-ops/intercept/create": {"order_id": 1, "reason": "test"},
    "order-ops/export/create": {"type": "csv"},
    # 库存
    "inventory/check": {"product_id": 1, "warehouse_id": 1, "quantity": 1},
    # 关税计算
    "tariff/calculate": {"product_id": 1, "country_code": "US", "price": 100},
    # 直播
    "live/rooms": {"title": "test", "anchor_id": 1},
    # RBAC（name/code/username/email 均 UNIQUE，加时间戳后缀避免重复跑冲突）
    "rbac/roles": {"name": f"test_role_{int(time.time())}", "code": f"TEST_{int(time.time())}", "description": "auto-test"},
    "rbac/users": {"username": f"testuser_{int(time.time())}", "email": f"t_{int(time.time())}@t.com", "role_id": 1},
    # 价格
    "price/create": {"product_id": 1, "price": 99.99, "type": "RETAIL"},
    "price/update": {"id": 1, "price": 88.88},
    # 敏感词更新
    "sensitive/update": {"id": 1, "word": "test", "category": "POLITICS"},
    "sensitive/batch-delete": {"ids": [1]},
}


def get_body_for(path, method):
    """根据路径返回合适的 body，None 表示不带 body。"""
    if method == "GET":
        return None
    if method == "DELETE":
        return None
    # POST/PUT：根据 path 匹配最小 body
    for key, body in MINIMAL_BODIES.items():
        if path.endswith(key) or key in path:
            return body
    # 含 ${id} 路径参数
    if "${" in path:
        return {"id": 1}
    # 字符串拼接 /xxx/' + id 模式
    if re.search(r"['\"]\s*\+\s*\w", path):
        return {"id": 1}
    return {}


def normalize_path(path):
    """把 ${id} 替换为 1；处理 admin.js 字符串拼接 '/path/' + id 模式。
    当正则只提取到前导字符串部分（结尾是 `/`）时，追加 /1 作为兜底。
    """
    # ${id} 占位符
    p = re.sub(r"\$\{[^}]+\}", "1", path)
    # 字符串拼接 '/path/' + id 模式：捕获的 path 以 / 结尾时追加 1
    if p.endswith("/"):
        p = p + "1"
    return p


def main():
    print("=" * 80)
    print("【阶段2-改进：基于 admin.js 真实端点的 API 测试（带最小有效 body）】")
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
        url_path = normalize_path(path)
        url = f"{ADMIN_BASE}{url_path}"
        body = get_body_for(path, method)
        s, p = http(method, url, token=token, body=body)
        bcode = p.get("code") if isinstance(p, dict) else None
        # 判定：端点可达 + 不报错 = OK
        # 业务校验失败 (400) 也算端点存在 = OK
        ok = s == 200 or (s in (400, 405) and bcode in (400, 405))
        # 409 在改进版下不应该出现（因为已发送有效 body）
        if s == 409:
            ok = False
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

    print(f"\n=== 阶段2-改进 总结: {ok_cnt} PASS / {err_cnt} FAIL / {len(unique)} TOTAL ===\n")
    print("=== 各方法统计 ===")
    for m, (o, e) in sorted(by_method.items()):
        print(f"  {m}: {o} OK / {e} FAIL")
    if results:
        print("\n=== 失败端点详情 ===")
        for method, path, s, p in results:
            print(f"- {method} {path} | status={s} body={json.dumps(p, ensure_ascii=False)[:300]}")


if __name__ == "__main__":
    main()
