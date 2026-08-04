#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""阶段2 关键接口数据结构验证"""
import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import secrets
import re
from pathlib import Path

BASE = "http://localhost:8080"
EMAIL = "admin@moyuyo.com"
PASSWORD = "123456"

env_path = Path(r"D:\MOYUYOWPC\moyuyo-server\.env")
env = {}
if env_path.exists():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
SIGN_SECRET = env.get("API_SIGN_SECRET", "")

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


def get(token, path, params=None):
    url = f"{BASE}{path}"
    if params:
        from urllib.parse import urlencode
        url = url + "?" + urlencode(params)
    headers = {"Authorization": f"Bearer {token}"}
    if not any(path.startswith(p) for p in SKIP_SIGN_PATHS):
        headers.update(make_sign_headers("GET", path))
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return 0, str(e)


def main():
    print("登录中...")
    token = login()
    print(f"Token: {token[:20]}...\n")

    # 验证关键接口返回的数据结构
    test_cases = [
        # (路径, 是否应为数组, 描述)
        ("/api/admin/dashboard/stats", "object", "仪表盘统计"),
        ("/api/admin/orders/list", "array", "订单列表"),
        ("/api/admin/users/list", "array", "用户列表"),
        ("/api/admin/products/list", "array", "商品列表"),
        ("/api/admin/finance/overview", "object", "财务概览"),
        ("/api/admin/logistics/warehouses", "array", "仓库列表"),
        ("/api/admin/cms/list", "array", "CMS内容列表"),
        ("/api/admin/rbac/roles", "array", "RBAC角色"),
        ("/api/admin/marketing/campaigns", "array", "营销活动"),
        ("/api/admin/coupons/list", "array", "优惠券"),
        ("/api/admin/flash-sales/list", "array", "秒杀"),
        ("/api/admin/audit-log/list", "array", "审计日志"),
        ("/api/admin/risk/rules", "array", "风控规则"),
        ("/api/admin/refunds/list", "array", "退款列表"),
        ("/api/admin/inventory/list", "array", "库存列表"),
        ("/api/admin/sms/records", "array", "短信记录"),
        ("/api/admin/sensitive/list", "array", "敏感词"),
        ("/api/admin/ticket/list", "array", "工单列表"),
        ("/api/admin/content-review/list", "array", "内容审核"),
        ("/api/admin/push/records", "array", "推送记录"),
        ("/api/admin/complaint/list", "array", "投诉列表"),
        ("/api/admin/review/list", "array", "评价列表"),
        ("/api/admin/crm/cs-performance", "array", "客服绩效"),
        ("/api/admin/blacklist/list", "array", "黑名单"),
        ("/api/admin/cs-sessions/list", "array", "客服会话"),
        ("/api/admin/order-tags/list", "array", "订单标签"),
        ("/api/admin/risk-alert/configs", "array", "风险告警配置"),
        ("/api/admin/inventory-transfer/list", "array", "库存调拨"),
        ("/api/admin/points/activities", "array", "积分活动"),
        ("/api/admin/gdpr/consent-records", "array", "GDPR同意"),
        ("/api/admin/satisfaction/list", "array", "满意度"),
        ("/api/admin/knowledge-base/list", "array", "知识库"),
        ("/api/admin/live/rooms", "array", "直播间"),
        ("/api/admin/product-approval/list", "array", "商品审批"),
        ("/api/admin/analysis/funnel", "object", "漏斗分析"),
        ("/api/admin/analysis/rfm", "object", "RFM分析"),
        ("/api/admin/system/config", "object", "系统配置"),
        ("/api/admin/system-info/info", "object", "系统信息"),
        ("/api/admin/settings/payment-methods", "array", "支付方式"),
        ("/api/admin/batch-import/records", "array", "批量导入记录"),
        ("/api/admin/app-version/list", "array", "App版本"),
        ("/api/admin/audit-log/stats", "object", "审计日志统计"),
        ("/api/admin/ticket/stats", "object", "工单统计"),
        ("/api/admin/orders/stats", "object", "订单统计"),
        ("/api/admin/users/stats", "object", "用户统计"),
    ]

    print(f"测试 {len(test_cases)} 个关键接口的数据结构...\n")
    print("=" * 100)
    print(f"{'接口':<50} {'code':<6} {'data-type':<12} {'rows':<8} {'status':<10}")
    print("=" * 100)

    ok = 0
    fail = 0
    fails = []
    for path, expected, desc in test_cases:
        code, body = get(token, path)
        if code != 200:
            status = f"FAIL({code})"
            fail += 1
            fails.append((path, desc, code, body if isinstance(body, str) else str(body)[:200]))
            print(f"{path:<50} {code:<6} {'-':<12} {'-':<8} {status:<10}")
            continue

        data = body.get("data") if isinstance(body, dict) else None
        # 跳过页码的分页返回
        if isinstance(data, dict) and "records" in data:
            data = data.get("records") or data.get("list")
        data_type = type(data).__name__
        rows = len(data) if isinstance(data, (list, dict)) else 0

        if expected == "array":
            if isinstance(data, list):
                status = "OK"
                ok += 1
            else:
                status = f"WRONG_TYPE({data_type})"
                fail += 1
                fails.append((path, desc, code, f"expected array, got {data_type}: {str(data)[:100]}"))
        elif expected == "object":
            if isinstance(data, (dict, list)):
                status = "OK"
                ok += 1
            else:
                status = f"WRONG_TYPE({data_type})"
                fail += 1
                fails.append((path, desc, code, f"expected object/array, got {data_type}"))
        else:
            status = "OK"
            ok += 1

        print(f"{path:<50} {code:<6} {data_type:<12} {str(rows):<8} {status:<10}")

    print("=" * 100)
    print(f"\n总计: {len(test_cases)}, 通过: {ok}, 失败: {fail}")
    if fails:
        print("\n失败详情:")
        for path, desc, code, body in fails:
            print(f"  - {path} ({desc}) [{code}]: {body[:150]}")
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    import sys
    sys.exit(main())
