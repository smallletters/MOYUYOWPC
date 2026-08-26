import pymysql, json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id, parent_id, name, icon, sort, level FROM mo_category ORDER BY level, sort")
print("=== DB mo_category (id, parent, name, icon, sort, level) ===")
for r in cur.fetchall():
    print(r)
conn.close()

print()
print("=== Backend /api/v1/categories summary ===")
import requests
r = requests.get("http://localhost:8080/api/v1/categories")
data = r.json().get("data")
for c in data:
    sub_count = len(c.get("children", []))
    print(f"  L1: {c['name']} (id={c['id']})  sub={sub_count}")
    for ch in c.get("children", []):
        print(f"    L2: {ch['name']} (id={ch['id']}, parentId={ch['parentId']})")