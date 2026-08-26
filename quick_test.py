"""快速验证积分核心接口"""
import urllib.request, json

def call(method, path, token=None, body=None):
    req = urllib.request.Request(f'http://localhost:8080{path}', method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    data = json.dumps(body).encode() if body else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=10) as resp:
            return json.loads(resp.read())
    except Exception as e:
        return {'err': str(e)}

# 登录
r = call('POST', '/api/v1/auth/login', body={'email': 'test@moyuyo.com', 'password': '012345678910'})
token = r['data']['accessToken']
print('登录成功, token 前缀:', token[:20])

# 1. 余额
print('\n1. GET /points/balance:', call('GET', '/api/v1/points/balance', token=token))

# 2. 积分礼品
print('\n2. GET /points/goods:')
goods = call('GET', '/api/v1/points/goods', token=token)['data']
print(f'   礼品数: {len(goods)}')
for g in goods:
    print(f'   - id={g["id"]} name={g["name"]} points={g["points"]} needAddress={g["needAddress"]}')

# 3. 抽奖活动
print('\n3. GET /lotteries:')
lott = call('GET', '/api/v1/lotteries', token=token)['data']
print(f'   抽奖活动数: {len(lott)}')
for lt in lott:
    print(f'   - id={lt["id"]} name={lt["name"]} prob={lt["probability"]} freePerDay={lt["dailyFree"]} cost={lt["pointsCost"]}')

# 4. 任务（按类型分组）
print('\n4. GET /missions/grouped:')
gm = call('GET', '/api/v1/missions/grouped', token=token)['data']
print(f'   daily={len(gm["daily"])} weekly={len(gm["weekly"])} achievements={len(gm["achievements"])}')

# 5. 签到（重复第二次应失败）
print('\n5. POST /points/checkin:', call('POST', '/api/v1/points/checkin', token=token))

# 6. 补签（首次免费）
print('\n6. POST /points/checkin/makeup:', call('POST', '/api/v1/points/checkin/makeup', token=token))

# 7. 会员等级
print('\n7. GET /member/levels:')
lv = call('GET', '/api/v1/member/levels', token=token)['data']
print(f'   等级数: {len(lv)}')
for l in lv:
    print(f'   - {l["code"]} {l["name"]} 成长值门槛={l["growthThreshold"]} 倍率={l["pointsRate"]}')

# 8. 邀请码
print('\n8. GET /invites/code:', call('GET', '/api/v1/invites/code', token=token))

print('\n=== 所有关键接口验证完成 ===')