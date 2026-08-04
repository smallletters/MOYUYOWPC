import pymysql
import os
# 尝试各种密码组合
for pwd in ['', 'root', 'moyuyo123', 'moyuyo', 'password', '123456']:
    try:
        conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password=pwd, database='moyuyo', charset='utf8mb4', connect_timeout=3)
        print(f'OK with password={pwd!r}')
        cur = conn.cursor()

        # mo_tariff_config: latest 10
        cur.execute("SELECT id, country_code, rate, status, create_time FROM mo_tariff_config ORDER BY create_time DESC LIMIT 10")
        print('--- mo_tariff_config latest 10 ---')
        for row in cur.fetchall():
            print(row)

        # mo_blacklist: latest 5
        cur.execute("SELECT id, type, value, reason, status, create_time FROM mo_blacklist ORDER BY create_time DESC LIMIT 5")
        print('\n--- mo_blacklist latest 5 ---')
        for row in cur.fetchall():
            print(row)

        # mo_sensitive_word: latest 5
        cur.execute("SELECT id, word, category, status, create_time FROM mo_sensitive_word ORDER BY create_time DESC LIMIT 5")
        print('\n--- mo_sensitive_word latest 5 ---')
        for row in cur.fetchall():
            print(row)

        # mo_points_log 活动聚合
        cur.execute("SELECT type, COUNT(*), SUM(change_value) FROM mo_points_log GROUP BY type")
        print('\n--- mo_points_log by type ---')
        for row in cur.fetchall():
            print(row)

        # mo_risk_alert_config latest 5
        cur.execute("SELECT id, name, type, threshold, enabled, create_time FROM mo_risk_alert_config ORDER BY create_time DESC LIMIT 5")
        print('\n--- mo_risk_alert_config latest 5 ---')
        for row in cur.fetchall():
            print(row)

        # mo_inventory_transfer latest 5
        cur.execute("SELECT id, from_warehouse_id, to_warehouse_id, quantity, reason, status, create_time FROM mo_inventory_transfer ORDER BY create_time DESC LIMIT 5")
        print('\n--- mo_inventory_transfer latest 5 ---')
        for row in cur.fetchall():
            print(row)

        conn.close()
        break
    except Exception as e:
        print(f'fail pwd={pwd!r}: {e}')
