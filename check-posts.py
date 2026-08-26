import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id, user_id, content, status, likes, comments, create_time FROM mo_community_post ORDER BY id DESC LIMIT 10")
for r in cur.fetchall(): print(r)
conn.close()