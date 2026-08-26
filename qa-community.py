import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}

results = []

def check(name, ok, detail=""):
    s = "PASS" if ok else "FAIL"
    results.append((s, name, detail))
    print(f"  [{s}] {name}  {detail}")

print("=== 1. 列表数据完整性 ===")
for tab_path in ["/community/posts", "/community/posts?topic=宠物穿搭", "/community/posts?keyword="]:
    r = requests.get(f"{BASE}{tab_path}", params={"size":10}, headers=H)
    data = r.json().get('data', {})
    recs = data.get('records', [])
    print(f"  {tab_path} → records={len(recs)}")

print()
print("=== 2. 帖子字段完整性（取第 1 条）===")
r = requests.get(f"{BASE}/community/posts", params={"size":1}, headers=H)
p = r.json().get('data',{}).get('records',[{}])[0]
required = ['id','userId','username','content','images','topic','likes','comments','liked','status','createTime']
missing = [k for k in required if k not in p]
check("帖子字段", not missing, f"missing={missing}" if missing else "全部字段都有")
check("帖子有用户名", bool(p.get('username')), f"username={p.get('username')}")
check("帖子有头像", bool(p.get('avatar')), f"avatar={p.get('avatar')}")
check("帖子有时间", bool(p.get('createTime')), f"createTime={p.get('createTime')}")
check("帖子有图片", isinstance(p.get('images'), list) and len(p.get('images',[])) > 0,
      f"images={p.get('images')}")
check("帖子有点赞数", isinstance(p.get('likes'), int), f"likes={p.get('likes')}")
check("帖子有评论数", isinstance(p.get('comments'), int), f"comments={p.get('comments')}")

print()
print("=== 3. 关注流联动 ===")
# 让 bob 发新帖，检查关注流
import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id FROM mo_user WHERE email='bob@example.com'")
bob_id = cur.fetchone()[0]
requests.post(f"{BASE}/follows", json={"targetId":bob_id}, headers=H)
cur.execute("SELECT COALESCE(MAX(id),0)+1 FROM mo_community_post")
nid = cur.fetchone()[0]
cur.execute("INSERT INTO mo_community_post (id, user_id, content, status, create_time) VALUES (%s, %s, '关注流测试帖', 1, NOW())",
            (nid, bob_id))
conn.commit()
conn.close()
r = requests.get(f"{BASE}/follows/feed", headers=H)
records = r.json().get('data',{}).get('records',[])
check("关注 bob 后可见 bob 帖子", len(records) >= 1 and any(r2.get('content') == '关注流测试帖' for r2 in records),
      f"records={len(records)}")
requests.delete(f"{BASE}/follows/{bob_id}", headers=H)

print()
print("=== 4. 限流（每个用户发帖 120/min）===")
import time
start = time.time()
ok = 0
for i in range(5):
    r = requests.post(f"{BASE}/community/posts", json={"content":f"压测{i}"}, headers=H)
    if r.status_code == 200: ok += 1
check("5 连发成功", ok == 5, f"ok={ok}/5 cost={time.time()-start:.1f}s")

print()
print("=== 5. 边界场景 ===")
# 1) 搜索特殊字符
r = requests.get(f"{BASE}/community/search", params={"q":"%_%"}, headers=H)
check("LIKE 元字符注入防护", r.status_code == 200, f"status={r.status_code} records={len(r.json().get('data',{}).get('records',[]))}")
# 2) 大分页
r = requests.get(f"{BASE}/community/posts", params={"page":9999,"size":20}, headers=H)
check("超出范围分页", r.status_code == 200, f"records={len(r.json().get('data',{}).get('records',[]))}")
# 3) 未登录访问
r = requests.get(f"{BASE}/community/posts/1", headers={})
check("未登录访问受保护端点(关注)", r.status_code in [401,403], f"status={r.status_code}")

print()
print("=== 6. 性能基准（10 次连续请求）===")
import time
t0 = time.time()
for i in range(10):
    requests.get(f"{BASE}/community/posts", params={"size":20}, headers=H)
avg = (time.time()-t0)/10*1000
check(f"平均响应 < 300ms", avg < 300, f"avg={avg:.0f}ms")

print()
print("=" * 60)
total_pass = sum(1 for r in results if r[0]=='PASS')
print(f"\n总计: {total_pass}/{len(results)} 通过")