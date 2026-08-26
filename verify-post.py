import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# 发帖
r = requests.post(f"{BASE}/community/posts",
                  json={"content":"测试 222 "},
                  headers=H)
print("POST status:", r.status_code)
body = r.json()
print("POST body:", json.dumps(body, ensure_ascii=False, indent=2))
new_id = body.get("data", {}).get("id") if isinstance(body.get("data"), dict) else None
print("new id from data.id:", new_id)
# 帖子是否真存在
if new_id:
    r2 = requests.get(f"{BASE}/community/posts", headers=H)
    records = r2.json().get("data", {}).get("records", [])
    print(f"  total posts now: {len(records)}, ids: {[r['id'] for r in records]}")

# 评论
print()
r = requests.post(f"{BASE}/community/posts/1/comments",
                  json={"content":"测试评论 222"},
                  headers=H)
print("Comment status:", r.status_code)
print("Comment body:", r.text[:400])