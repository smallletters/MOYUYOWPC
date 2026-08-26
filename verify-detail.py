import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# 发帖看响应体
r = requests.post(f"{BASE}/community/posts",
                  json={"content":"测试内容 #猫"},
                  headers=H)
print("POST status:", r.status_code)
print("POST body:", json.dumps(r.json(), ensure_ascii=False, indent=2)[:800])

# 评论看错误
r = requests.post(f"{BASE}/community/posts/1/comments",
                  json={"content":"测试评论"},
                  headers=H)
print()
print("Comment status:", r.status_code)
print("Comment body:", r.text[:600])