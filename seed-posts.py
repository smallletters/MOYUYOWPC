import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("SELECT id FROM mo_user WHERE email='test@moyuyo.com'")
uid = cur.fetchone()[0]
print('user:', uid)

# 清掉旧的，再插入带 likes/comments 的
cur.execute("DELETE FROM mo_community_post WHERE user_id=%s", (uid,))

posts = [
    ('今天出门给 Luna 穿了新买的防风夹克，回头率超高～', '["/uploads/community-cat-ootd.jpg"]', 2, 328, 56),
    ('带金毛去爬山，强烈推荐这套经典牵引套装，超轻便！', '["/uploads/community-dog-hike.jpg"]', 5, 512, 89),
    ('入夏后猫咪容易油尾巴，分享我的居家护理流程～', '["/uploads/community-cat-care.jpg"]', 24, 196, 34),
]
cur.execute("SELECT COALESCE(MAX(id),0)+1 FROM mo_community_post")
nid = cur.fetchone()[0]
for i, (content, images, hours_ago, likes, comments) in enumerate(posts):
    pid = nid + i
    cur.execute(
        "INSERT INTO mo_community_post (id, user_id, content, images, status, view_count, likes, comments, create_time) VALUES (%s, %s, %s, %s, 'PUBLISHED', %s, %s, %s, NOW() - INTERVAL %s HOUR)",
        (pid, uid, content, images, 100*(i+1), likes, comments, hours_ago))
conn.commit()
print('Inserted posts:', [nid, nid+1, nid+2])
conn.close()