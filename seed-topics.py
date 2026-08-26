import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
# 看现有
cur.execute("SELECT id, name, active FROM mo_community_topic_v2")
for r in cur.fetchall(): print(r)
# 插入 5 个 PRD 话题
topics = [
    ('宠物穿搭', 100, 1),
    ('夏日护理', 90, 2),
    ('新品速递', 80, 3),
    ('猫咪专区', 95, 4),
    ('狗狗日常', 85, 5),
    ('户外徒步', 70, 6),
    ('居家护理', 60, 7),
    ('新手养宠', 50, 8),
]
cur.execute("SELECT COALESCE(MAX(id),0)+1 FROM mo_community_topic_v2")
nid = cur.fetchone()[0]
for i, (name, hot, sort) in enumerate(topics):
    tid = nid + i
    cur.execute(
        "INSERT INTO mo_community_topic_v2 (id, name, hot, sort_order, active, create_time) VALUES (%s, %s, %s, %s, 1, NOW())",
        (tid, name, hot, sort))
conn.commit()
print("Inserted:", [nid+i for i in range(len(topics))])
conn.close()