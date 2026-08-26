import requests, json
BASE = "http://localhost:8080/api/v1"

r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
print("LOGIN:", r.status_code)
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}

# 再次请求
tests = [
    ("GET", "/help/categories", None, True),     # 公开
    ("GET", "/help/articles", None, True),
    ("GET", "/users/1/profile", None, True),       # 公开
    ("GET", "/prime/plans", None, True),          # 公开
    ("GET", "/festivals/active", None, True),     # 公开
    ("GET", "/topics", None, True),               # 公开
    ("GET", "/posts/collected", None, False),
    ("GET", "/follows/1/status", None, False),    # 实际是 GET /follows/{targetId}/status
    ("POST", "/coupons/1/transfer?toUserId=2", None, False),  # 测试 transfer
]
for method, path, body, public in tests:
    if public:
        r = requests.request(method, BASE+path, headers=H)
    else:
        r = requests.request(method, BASE+path, headers=H)
    print(f"{method} {path}: {r.status_code} | {r.text[:160]}")

print("DONE")