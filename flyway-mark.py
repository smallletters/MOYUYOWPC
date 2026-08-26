import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("INSERT IGNORE INTO schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (68, '20260826.01', 'seed category full', 'SQL', 'V20260826_01__seed_category_full.sql', NULL, 'admin', NOW(), 100, 1)")
conn.commit()
print("Flyway marked:", cur.rowcount)
cur.execute("SELECT version, installed_rank FROM schema_history ORDER BY installed_rank DESC LIMIT 3")
for r in cur.fetchall(): print(r)
conn.close()