# -*- coding: utf-8 -*-
"""执行 ALTER TABLE 将 condition_json 改为 TEXT"""
import pymysql

conn = pymysql.connect(host='localhost', port=3306, user='root', password='',
                       database='moyuyo_dev', charset='utf8mb4')
cur = conn.cursor()
cur.execute("ALTER TABLE `mo_risk_rule` MODIFY COLUMN `condition_json` TEXT NOT NULL COMMENT '触发条件'")
conn.commit()
cur.execute("SHOW COLUMNS FROM mo_risk_rule LIKE 'condition_json'")
print('condition_json 列:', cur.fetchone())
conn.close()
