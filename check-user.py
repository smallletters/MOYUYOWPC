import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
# 真实用户的帖子
cur.execute("""SELECT p.id, u.nickname, u.avatar, p.images, p.likes, p.content
FROM mo_community_post p JOIN mo_user u ON p.user_id=u.id
WHERE p.status=1 ORDER BY p.id LIMIT 5""")
for r in cur.fetchall(): print(r)
conn.close()