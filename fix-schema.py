import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()

# 1. mo_sensitive_word 表结构
cur.execute("DESCRIBE mo_sensitive_word")
print("=== mo_sensitive_word ===")
for r in cur.fetchall(): print(r)

cur.execute("DESCRIBE mo_community_comment")
print()
print("=== mo_community_comment ===")
for r in cur.fetchall(): print(r)

# 补列
cur.execute("ALTER TABLE mo_sensitive_word ADD COLUMN IF NOT EXISTS last_hit_time DATETIME NULL")
print("Added mo_sensitive_word.last_hit_time")
cur.execute("ALTER TABLE mo_community_comment ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0")
print("Added mo_community_comment.deleted")
conn.commit()
conn.close()