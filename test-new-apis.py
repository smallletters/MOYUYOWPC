import requests, json
BASE = "http://localhost:8080/api/v1"

# 1. 登录
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
print("LOGIN:", r.status_code, r.text[:200])
if r.status_code != 200:
    raise SystemExit("login failed")
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}"}

# 2. 客服系统
for p in ["/cs/sessions", "/cs/unread-count"]:
    r = requests.get(BASE+p, headers=H)
    print(f"{p}: {r.status_code} body={r.text[:120]}")

# 3. 帮助中心
r = requests.get(f"{BASE}/help/categories")
print("help/categories:", r.status_code, r.text[:120])
r = requests.get(f"{BASE}/help/articles")
print("help/articles:", r.status_code, r.text[:120])

# 4. 设备
r = requests.get(f"{BASE}/devices", headers=H)
print("devices:", r.status_code, r.text[:120])

# 5. 关注
r = requests.get(f"{BASE}/follows/status?targetId=1", headers=H)
print("follows/status:", r.status_code, r.text[:120])

# 6. 他人主页
r = requests.get(f"{BASE}/users/1/profile")
print("users/1/profile:", r.status_code, r.text[:200])

# 7. 黑名单
r = requests.get(f"{BASE}/blocks", headers=H)
print("blocks:", r.status_code, r.text[:120])

# 8. Prime
r = requests.get(f"{BASE}/prime/plans")
print("prime/plans:", r.status_code, r.text[:120])

# 9. Affiliate
r = requests.get(f"{BASE}/affiliate/account", headers=H)
print("affiliate/account:", r.status_code, r.text[:200])

# 10. 预约
r = requests.get(f"{BASE}/bookings", headers=H)
print("bookings:", r.status_code, r.text[:120])

# 11. 活动
r = requests.get(f"{BASE}/festivals/active")
print("festivals/active:", r.status_code, r.text[:120])

# 12. 新人
r = requests.get(f"{BASE}/newuser/gifts", headers=H)
print("newuser/gifts:", r.status_code, r.text[:120])

# 13. 成就
r = requests.get(f"{BASE}/achievements", headers=H)
print("achievements:", r.status_code, r.text[:200])

# 14. 年报
r = requests.get(f"{BASE}/reports/annual", headers=H)
print("reports/annual:", r.status_code, r.text[:200])

# 15. 社区 - 收藏
r = requests.get(f"{BASE}/posts/collected", headers=H)
print("posts/collected:", r.status_code, r.text[:120])
r = requests.get(f"{BASE}/topics")
print("topics:", r.status_code, r.text[:120])
print("ALL DONE")