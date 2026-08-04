# -*- coding: utf-8 -*-
"""查询 mo_risk_rule 表，确认 rule_code 冲突"""
import pymysql

conn = pymysql.connect(host='localhost', port=3306, user='root', password='',
                       database='moyuyo_dev', charset='utf8mb4')
cur = conn.cursor()
cur.execute("SELECT id, rule_code, rule_name, create_time FROM mo_risk_rule ORDER BY create_time DESC LIMIT 12")
rows = cur.fetchall()
for r in rows:
    print(r)
# 统计重复 rule_code
cur.execute("SELECT rule_code, COUNT(*) c FROM mo_risk_rule GROUP BY rule_code HAVING c > 1")
dups = cur.fetchall()
print('重复 rule_code:', dups if dups else '无')
conn.close()
