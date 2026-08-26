import requests, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = "http://localhost:8080/api/v1"
r = requests.post(f"{BASE}/auth/login", json={"email":"test@moyuyo.com","password":"012345678910"})
token = r.json()["data"]["accessToken"]
H = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

# 发帖
r = requests.post(f"{BASE}/community/posts",
                  json={"content":"测试发帖 333 "},
                  headers=H)
print("status:", r.status_code)
print("body:", json.dumps(r.json(), ensure_ascii=False, indent=2)[:800])

# 验证帖子是否入库
import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id, content, status FROM mo_community_post ORDER BY id DESC LIMIT 3")
for r in cur.fetchall(): print(r)
conn.close()