# -*- coding: utf-8 -*-
import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='', database='moyuyo_dev', charset='utf8mb4')
cur = conn.cursor()
cur.execute("SHOW COLUMNS FROM mo_risk_alert_config LIKE 'alert_type'")
print('alert_type:', cur.fetchone())
cur.execute("SELECT installed_rank, version, description, success FROM schema_history ORDER BY installed_rank DESC LIMIT 3")
for r in cur.fetchall():
    print(r)
conn.close()
