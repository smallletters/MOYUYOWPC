import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# 详情 (实际日志会写 no.trace 但带 traceId)
r = requests.get(f"{BASE}/community/posts/1", headers=H)
print("GET post detail status:", r.status_code)
print("body:", r.text[:400])

print()
print("=" * 60)
r = requests.post(f"{BASE}/community/posts", json={"content":"测试 555 "}, headers=H)
print("POST status:", r.status_code)
print("body:", r.text[:400])

print()
print("=" * 60)
r = requests.post(f"{BASE}/community/posts/1/comments", json={"content":"测试评论 555"}, headers=H)
print("Comment status:", r.status_code)
print("body:", r.text[:400])