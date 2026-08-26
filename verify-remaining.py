import requests, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"

# 登录
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}

def safe(label, ok, detail=""):
    print(f"  [{'OK' if ok else 'FAIL'}] {label}  {detail}")

print("=" * 60)
print("[1] 社区独立搜索端点：GET /community/search?q=")
print("=" * 60)
r = requests.get(f"{BASE}/community/search", params={"q":"猫","size":5}, headers=H)
safe("/community/search?q=猫", r.status_code == 200 and len(r.json().get('data',{}).get('records',[])) > 0,
      f"status={r.status_code} count={len(r.json().get('data',{}).get('records',[]))}")

r = requests.get(f"{BASE}/community/search", params={"q":"携行","size":5}, headers=H)
safe("/community/search?q=携行 (empty)", r.status_code == 200 and len(r.json().get('data',{}).get('records',[])) == 0,
      f"count={len(r.json().get('data',{}).get('records',[]))}")

r = requests.get(f"{BASE}/community/posts", params={"keyword":"猫","size":5}, headers=H)
safe("/community/posts?keyword=猫", r.status_code == 200 and len(r.json().get('data',{}).get('records',[])) > 0,
      f"status={r.status_code} count={len(r.json().get('data',{}).get('records',[]))}")

print()
print("=" * 60)
print("[2] 关注 Tab 数据源：GET /follows/feed")
print("=" * 60)

# 先关注自己发的几个帖子的人（测试用户已关注了 180000002）
r = requests.get(f"{BASE}/follows/feed", headers=H)
records = r.json().get('data',{}).get('records',[])
safe("/follows/feed (未关注任何人)", r.status_code == 200 and len(records) == 0,
      f"status={r.status_code} records={len(records)}")

# 关注 180000002 (bob)
requests.post(f"{BASE}/follows", json={"targetId":180000002}, headers=H)

# 让 bob 发帖
import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id FROM mo_user WHERE email='bob@example.com'")
bob_id = cur.fetchone()[0]
cur.execute("SELECT COALESCE(MAX(id),0)+1 FROM mo_community_post")
nid = cur.fetchone()[0]
cur.execute(
    "INSERT INTO mo_community_post (id, user_id, content, status, like_count, comment_count, create_time) VALUES (%s, %s, '今天带金毛去了宠物公园', 1, 0, 0, NOW())",
    (nid, bob_id))
conn.commit()
conn.close()

# 再次拉 feed
r = requests.get(f"{BASE}/follows/feed", headers=H)
records = r.json().get('data',{}).get('records',[])
sample = records[0] if records else None
safe("/follows/feed (关注了bob后)", r.status_code == 200 and len(records) >= 1,
      f"status={r.status_code} records={len(records)} sample={'YES' if sample and sample.get('username') else 'NO'}")

if sample:
    print(f"      sample: id={sample['id']} user={sample.get('username')} content={sample.get('content')[:30]}")

# 取消关注后
requests.delete(f"{BASE}/follows/180000002", headers=H)
r = requests.get(f"{BASE}/follows/feed", headers=H)
records = r.json().get('data',{}).get('records',[])
safe("/follows/feed (取关后)", r.status_code == 200 and len(records) == 0,
      f"records={len(records)}")