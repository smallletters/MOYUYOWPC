import requests, sys, io, pymysql
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"

r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}

# 先关注 bob 和 eva 两个用户
requests.post(f"{BASE}/follows", json={"targetId": 180000002}, headers=H)  # bob
requests.post(f"{BASE}/follows", json={"targetId": 180000005}, headers=H)  # eva

# 验证 /follows/following 含用户信息
r = requests.get(f"{BASE}/follows/following", headers=H)
records = r.json().get('data', [])
print(f"=== /follows/following: {len(records)} 条 ===")
for x in records:
    has_nick = bool(x.get('nickname'))
    print(f"  targetId={x['targetId']} nickname={x.get('nickname')} avatar={'YES' if x.get('avatar') else 'NO'} hasNickname={has_nick}")

# 验证 /follows/followers
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("INSERT IGNORE INTO mo_follow (user_id, target_id, status, create_time) VALUES (%s, %s, 'FOLLOWING', NOW())",
            (180000002, 200000001))  # bob 关注 test
conn.commit()
conn.close()

r = requests.get(f"{BASE}/follows/followers", headers=H)
records = r.json().get('data', [])
print(f"\n=== /follows/followers: {len(records)} 条 ===")
for x in records:
    has_nick = bool(x.get('nickname'))
    print(f"  userId={x['userId']} nickname={x.get('nickname')} avatar={'YES' if x.get('avatar') else 'NO'} hasNickname={has_nick}")

# 清理
requests.delete(f"{BASE}/follows/180000002", headers=H)
requests.delete(f"{BASE}/follows/180000005", headers=H)
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("DELETE FROM mo_follow WHERE user_id=180000002 AND target_id=200000001")
conn.commit()
conn.close()
print("\nDONE")