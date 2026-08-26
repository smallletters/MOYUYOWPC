import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"

r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

def safe(label, ok, detail=""):
    print(f"  [{'OK' if ok else 'FAIL'}] {label}  {detail}")

def get_json(r):
    try:
        return r.json()
    except: return {}

print("=" * 60)
print("[1] 帖子列表 - 推荐 Tab")
print("=" * 60)
r = requests.get(f"{BASE}/community/posts", params={"page":1,"size":5}, headers=H)
records = get_json(r).get("data", {}).get("records", [])
safe("列表返回 3 条", len(records) >= 3, f"actual={len(records)}")
if records:
    print(f"      sample: id={records[0]['id']} user={records[0].get('username')} likes={records[0]['likes']} comments={records[0]['comments']}")

pid = records[0]["id"] if records else 1

print()
print("=" * 60)
print("[2] 点赞 / 取消点赞")
print("=" * 60)
r = requests.post(f"{BASE}/community/posts/{pid}/like", headers=H)
safe("点赞 POST", r.status_code == 200, f"status={r.status_code}")
r = requests.get(f"{BASE}/community/posts/{pid}", headers=H)
data = get_json(r).get("data") or {}
safe("详情含 liked=true", data.get("liked") is True, f"liked={data.get('liked')} likes={data.get('likes')}")
r = requests.delete(f"{BASE}/community/posts/{pid}/like", headers=H)
safe("取消点赞 DELETE", r.status_code == 200)
r = requests.get(f"{BASE}/community/posts/{pid}", headers=H)
data = get_json(r).get("data") or {}
safe("详情 liked=false", data.get("liked") in (False, None), f"liked={data.get('liked')}")

print()
print("=" * 60)
print("[3] 发布帖子")
print("=" * 60)
r = requests.post(f"{BASE}/community/posts",
                  json={"content":"测试发布内容 #宠物穿搭 来自APP验证",
                        "images":["/uploads/test.jpg"], "topic":"宠物穿搭"},
                  headers=H)
j = get_json(r)
new_id = j.get("data", {}).get("id") if r.status_code == 200 else None
safe("发帖成功", new_id is not None, f"new_id={new_id}")

print()
print("=" * 60)
print("[4] 评论")
print("=" * 60)
r = requests.post(f"{BASE}/community/posts/{pid}/comments",
                  json={"content":"好可爱的猫！"}, headers=H)
safe("评论成功", r.status_code == 200, f"status={r.status_code}")

print()
print("=" * 60)
print("[5] 关注接口（独立 Follow 模块）")
print("=" * 60)
r = requests.post(f"{BASE}/follows", json={"targetId": 180000002}, headers=H)
safe("POST /follows 关注", r.status_code == 200, f"status={r.status_code}")
r = requests.get(f"{BASE}/follows/180000002/status", headers=H)
data = get_json(r).get("data") or {}
safe("已关注状态", data.get("following") is True, f"following={data.get('following')}")
r = requests.get(f"{BASE}/follows/following", headers=H)
following = get_json(r).get("data") or []
safe("我关注列表 ≥ 1", len(following) >= 1, f"count={len(following)}")
r = requests.delete(f"{BASE}/follows/180000002", headers=H)
safe("DELETE /follows/{id} 取关", r.status_code == 200)

print()
print("=" * 60)
print("[6] 搜索帖子")
print("=" * 60)
# 尝试常见搜索路径
search_paths = [
    "/api/v1/community/posts?keyword=",
    "/api/v1/community/search?q=",
    "/api/v1/search/community?q=",
    "/api/v1/community/posts/search?q=",
]
for p in search_paths:
    full = "http://localhost:8080" + p + "猫"
    r = requests.get(full, headers=H)
    ok = (r.status_code == 200)
    detail = f"status={r.status_code} count={len(get_json(r).get('data', {}).get('records', [])) if ok else '-'}"
    safe(f"{p}", ok, detail)

print()
print("=" * 60)
print("[7] 我的帖子")
print("=" * 60)
r = requests.get(f"{BASE}/community/posts/mine", headers=H)
records2 = get_json(r).get("data", {}).get("records", [])
safe("我的帖子可拉取", r.status_code == 200, f"count={len(records2)}")

print()
print("=" * 60)
print("[8] 收藏 / 取消收藏 / 我的收藏")
print("=" * 60)
r = requests.post(f"{BASE}/community/posts/{pid}/collect", headers=H)
safe("收藏 POST", r.status_code == 200, f"status={r.status_code}")
r = requests.get(f"{BASE}/community/posts/collected", headers=H)
collected = get_json(r).get("data") or []
safe("我的收藏含此帖", pid in collected, f"collected={collected}")
r = requests.delete(f"{BASE}/community/posts/{pid}/collect", headers=H)
safe("取消收藏", r.status_code == 200)
r = requests.get(f"{BASE}/community/posts/collected", headers=H)
collected = get_json(r).get("data") or []
safe("取消后不在收藏", pid not in collected)

print()
print("=" * 60)
print("[9] 话题广场")
print("=" * 60)
r = requests.get(f"{BASE}/community/topics", headers=H)
topics = get_json(r).get("data") or []
safe("话题列表", r.status_code == 200 and len(topics) > 0, f"count={len(topics)}")