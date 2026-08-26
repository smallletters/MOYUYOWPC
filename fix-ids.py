import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()

# 把 mo_community_post.id 改为 AUTO_INCREMENT
cur.execute("ALTER TABLE mo_community_post MODIFY id BIGINT(20) NOT NULL AUTO_INCREMENT")
print("OK: mo_community_post.id -> AUTO_INCREMENT")

# CommunityCommentEntity 也有相同问题
cur.execute("DESCRIBE mo_community_comment")
print()
for r in cur.fetchall(): print(r)
conn.commit()
conn.close()