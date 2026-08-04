# -*- coding: utf-8 -*-
"""
写链路闭环验证脚本（UTF-8 编码）
验证：创建活动(POST) → 存库 → 列表回显(GET)，全程使用 UTF-8 编码 JSON。
同时清理历史 PowerShell 乱码测试数据。
"""
import json
import time
import urllib.request

BASE_API = "http://localhost:8080/api/admin"
BASE = BASE_API + "/marketing"


def call(method, path, body=None, token=None):
    """发送 HTTP 请求，body 以 UTF-8 编码"""
    url = BASE + path
    data = None
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        # 关键：json.dumps 默认 ensure_ascii=True 会转成 \uXXXX，
        # 但这里显式 ensure_ascii=False 并 encode('utf-8')，保证中文按 UTF-8 字节发送
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def login():
    """管理员登录获取 token"""
    body = json.dumps({"email": "admin@moyuyo.com", "password": "123456"}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE_API + "/auth/login", data=body, method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    assert result.get("code") == 0, f"登录失败: {result}"
    return result["data"]["token"]


def main():
    print("=" * 60)
    print("步骤0: 管理员登录")
    token = login()
    print("  登录成功, token 已获取")

    print("\n步骤1: 以 UTF-8 编码创建活动（含中文名称）")
    name = "验证活动_阶段4_UTF8"
    body = {
        "name": name,
        "type": "CUSTOM",
        "startDate": "2026-08-01",
        "endDate": "2026-08-31",
        "description": "阶段4写链路UTF-8编码验证",
        "budget": 5000.00,
    }
    status, result = call("POST", "/campaigns", body, token)
    print(f"  HTTP {status} -> {json.dumps(result, ensure_ascii=False)[:300]}")
    assert status == 200 and result.get("code") == 0, f"创建失败: {result}"
    data = result.get("data") or {}
    campaign_id = data.get("id") or data.get("campaignId")
    print(f"  创建成功, campaign id = {campaign_id}")

    print("\n步骤2: 按雪花ID查询活动详情验证回显")
    status, detail = call("GET", f"/campaigns/{campaign_id}", token=token)
    print(f"  HTTP {status} -> {json.dumps(detail, ensure_ascii=False)[:400]}")
    assert status == 200 and detail.get("code") == 0, f"详情查询失败: {detail}"
    d = detail.get("data") or {}
    echo_name = d.get("name") or d.get("campaignName")
    assert echo_name == name, f"回显名称不匹配: 期望[{name}] 实际[{echo_name}]"
    print(f"  回显成功: name=[{echo_name}] type=[{d.get('type')}] ✅ 写链路闭环成立")

    print("\n步骤3: 清理验证产生的测试数据")
    status, del_result = call("DELETE", f"/campaigns/{campaign_id}", token=token)
    print(f"  HTTP {status} -> {json.dumps(del_result, ensure_ascii=False)[:200]}")

    print("\n步骤4: 清理历史乱码测试数据 id=2083283586381041665")
    status, del2 = call("DELETE", "/campaigns/2083283586381041665", token=token)
    print(f"  HTTP {status} -> {json.dumps(del2, ensure_ascii=False)[:200]}")

    print("\n步骤5: 列表确认无残留（前15条含测试关键词即失败）")
    status, lst = call("GET", "/campaigns?page=1&size=15", token=token)
    items = ((lst.get("data") or {}).get("list")) or []
    names = [str(i.get("name", "")) for i in items]
    bad = [n for n in names if "验证活动" in n or "?" in n]
    print(f"  列表共 {len(items)} 条, 残留检查: {'发现 ' + str(bad) if bad else '无残留 ✅'}")
    print("=" * 60)
    print("写链路 UTF-8 验证完成")


if __name__ == "__main__":
    main()
