import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='', database='moyuyo_dev', charset='utf8mb4', connect_timeout=3)
cur = conn.cursor()

# mo_points_log: 看是否新增了 ADJUST 类型
cur.execute("SELECT type, COUNT(*) FROM mo_points_log GROUP BY type")
print('--- mo_points_log by type (after create attempts) ---')
for row in cur.fetchall():
    print(row)

# mo_risk_alert_config 表结构
cur.execute("DESCRIBE mo_risk_alert_config")
print('\n--- mo_risk_alert_config columns ---')
for row in cur.fetchall():
    print(row)

# mo_risk_alert_config 数据
cur.execute("SELECT * FROM mo_risk_alert_config ORDER BY create_time DESC LIMIT 3")
print('\n--- mo_risk_alert_config latest 3 ---')
for row in cur.fetchall():
    print(row)

# mo_inventory_transfer 表结构 + 数据
cur.execute("DESCRIBE mo_inventory_transfer")
print('\n--- mo_inventory_transfer columns ---')
for row in cur.fetchall():
    print(row)
cur.execute("SELECT * FROM mo_inventory_transfer ORDER BY create_time DESC LIMIT 3")
print('\n--- mo_inventory_transfer latest 3 ---')
for row in cur.fetchall():
    print(row)
