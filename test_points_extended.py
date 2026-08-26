"""验证新增的积分商城、补签、抽奖扣减接口。"""
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

# 0. 登录 test@moyuyo.com
print("=== 0. 登录 test 用户 ===")
r = call("/api/v1/auth/login", "POST", body={
    "email": "test@moyuyo.com",
    "password": "012345678910"
})
print(json.dumps(r, indent=2, ensure_ascii=False)[:300])
token = (r.get("data") or {}).get("accessToken")
if not token:
    print("❌ 登录失败"); exit(1)

# 1. 积分商城礼品列表
print("\n=== 1. GET /api/v1/points/goods ===")
r = call("/api/v1/points/goods", token=token)
print(f"共 {len(r.get('data', []))} 个礼品")
for g in r.get('data', [])[:3]:
    print(f"  - [{g.get('id')}] {g.get('name')} ({g.get('points')} 积分) 库存={g.get('stock')}")

# 2. 按分类过滤
print("\n=== 2. GET /api/v1/points/goods?category=COUPON ===")
r = call("/api/v1/points/goods", token=token, query={"category": "COUPON"})
print(f"COUPON 分类共 {len(r.get('data', []))} 个")

# 3. 积分余额（兑换前）
print("\n=== 3. GET /api/v1/points/balance (兑换前) ===")
r = call("/api/v1/points/balance", token=token)
print(f"当前余额: {r.get('data')}")

# 4. 兑换积分礼品（300 积分的 IP 贴纸套装，6号）
print("\n=== 4. POST /api/v1/points/goods/exchange (id=6, 虚拟礼品) ===")
r = call("/api/v1/points/goods/exchange", "POST", token=token, body={
    "goodsId": 6,
    "receiverName": "测试用户",
    "receiverPhone": "+12025550001",
    "receiverAddress": "上海浦东新区 100号"
})
print(json.dumps(r, indent=2, ensure_ascii=False)[:400])

# 5. 积分余额（兑换后）
print("\n=== 5. GET /api/v1/points/balance (兑换后) ===")
r = call("/api/v1/points/balance", token=token)
print(f"当前余额: {r.get('data')}")

# 6. 兑换记录
print("\n=== 6. GET /api/v1/points/goods/exchanges ===")
r = call("/api/v1/points/goods/exchanges", token=token)
print(f"兑换记录数: {len(r.get('data', []))}")
for e in r.get('data', [])[:2]:
    print(f"  - {e.get('goodsName')} 消耗{e.get('pointsCost')}积分 状态={e.get('status')}")

# 7. 漏签补签（首次免费）
print("\n=== 7. POST /api/v1/points/checkin/makeup (首次免费) ===")
r = call("/api/v1/points/checkin/makeup", "POST", token=token)
print(json.dumps(r, indent=2, ensure_ascii=False)[:300])

# 8. 漏签补签（第二次收费 50 积分）
print("\n=== 8. POST /api/v1/points/checkin/makeup (第二次扣 50) ===")
r = call("/api/v1/points/checkin/makeup", "POST", token=token)
print(json.dumps(r, indent=2, ensure_ascii=False)[:300])

# 9. 抽奖：找一个启用的 lottery
print("\n=== 9. GET /api/v1/lotteries ===")
r = call("/api/v1/lotteries", token=token)
lotteries = r.get('data', [])
print(f"共 {len(lotteries)} 个抽奖活动")
for lt in lotteries[:3]:
    print(f"  - [{lt.get('id')}] {lt.get('name')} 概率={lt.get('probability')} 单次={lt.get('pointsCost')}积分")

# 10. 抽奖一次
if lotteries:
    lottery_id = lotteries[0]['id']
    print(f"\n=== 10. POST /api/v1/lotteries/{lottery_id}/spin ===")
    r = call(f"/api/v1/lotteries/{lottery_id}/spin", "POST", token=token)
    print(json.dumps(r, indent=2, ensure_ascii=False)[:400])

print("\n=== ✅ 所有积分扩展接口测试完成 ===")