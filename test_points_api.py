"""测试积分规则后端接口：登录 -> 签到 -> 拉任务列表 -> 领取奖励"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"

def call(path, method="GET", token=None, body=None, query=None):
    url = BASE + path
    if query:
        from urllib.parse import urlencode
        url += "?" + urlencode(query)
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return {"http_error": e.code, "body": e.read().decode()}

# 1. 注册
print("=== 1. 注册测试用户 ===")
r = call("/api/v1/auth/register", "POST", body={
    "email": "points-demo@moyuyo.com",
    "password": "Demo123!",
    "nickname": "积分演示",
    "country": "US"
})
print(json.dumps(r, indent=2, ensure_ascii=False)[:400])

if "data" not in r or "token" not in r.get("data", {}):
    # 用户可能已存在，尝试登录
    print("\n=== 2. 登录已有用户 ===")
    r = call("/api/v1/auth/login", "POST", body={
        "email": "points-demo@moyuyo.com",
        "password": "Demo123!"
    })
    print(json.dumps(r, indent=2, ensure_ascii=False)[:400])

token = r.get("data", {}).get("accessToken") or r.get("data", {}).get("token")
if not token:
    print("❌ 无法获取 token，退出测试")
    exit(1)

print(f"\n✅ Token 获取成功（前 30 字）: {token[:30]}...")

# 3. 拉会员信息
print("\n=== 3. GET /api/v1/member ===")
print(json.dumps(call("/api/v1/member", token=token), indent=2, ensure_ascii=False)[:400])

# 4. 拉会员等级档位
print("\n=== 4. GET /api/v1/member/levels ===")
print(json.dumps(call("/api/v1/member/levels", token=token), indent=2, ensure_ascii=False)[:600])

# 5. 当前用户积分倍率
print("\n=== 5. GET /api/v1/member/points-rate ===")
print(json.dumps(call("/api/v1/member/points-rate", token=token), indent=2, ensure_ascii=False))

# 6. 签到
print("\n=== 6. POST /api/v1/points/checkin ===")
print(json.dumps(call("/api/v1/points/checkin", "POST", token=token), indent=2, ensure_ascii=False)[:400])

# 7. 重复签到（应失败）
print("\n=== 7. POST /api/v1/points/checkin (重复，应失败) ===")
print(json.dumps(call("/api/v1/points/checkin", "POST", token=token), indent=2, ensure_ascii=False)[:400])

# 8. 积分余额
print("\n=== 8. GET /api/v1/points/balance ===")
print(json.dumps(call("/api/v1/points/balance", token=token), indent=2, ensure_ascii=False))

# 9. 积分流水
print("\n=== 9. GET /api/v1/points/log?page=1&size=5 ===")
print(json.dumps(call("/api/v1/points/log", token=token, query={"page": 1, "size": 5}), indent=2, ensure_ascii=False)[:800])

# 10. 任务列表（按类型分组）
print("\n=== 10. GET /api/v1/missions/grouped ===")
print(json.dumps(call("/api/v1/missions/grouped", token=token), indent=2, ensure_ascii=False)[:800])

# 11. 任务统计
print("\n=== 11. GET /api/v1/missions/stats ===")
print(json.dumps(call("/api/v1/missions/stats", token=token), indent=2, ensure_ascii=False))

# 12. 邀请码
print("\n=== 12. GET /api/v1/invites/code ===")
print(json.dumps(call("/api/v1/invites/code", token=token), indent=2, ensure_ascii=False)[:300])

# 13. 邀请统计
print("\n=== 13. GET /api/v1/invites/stats ===")
print(json.dumps(call("/api/v1/invites/stats", token=token), indent=2, ensure_ascii=False))

print("\n=== ✅ 所有积分相关接口测试完成 ===")