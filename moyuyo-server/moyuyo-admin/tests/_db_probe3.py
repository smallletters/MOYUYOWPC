import pymysql
try:
    conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='', charset='utf8mb4', connect_timeout=3)
    cur = conn.cursor()
    cur.execute("SHOW DATABASES")
    print('--- databases ---')
    for row in cur.fetchall():
        print(row[0])
    # moyuyo_dev or moyuyo
    db = None
    for name in ['moyuyo_dev', 'moyuyo']:
        try:
            cur.execute(f"USE {name}")
            db = name
            break
        except: pass
    if not db:
        print('not found moyuyo dev db')
        exit()
    print(f'using db={db}')
    cur.execute("SELECT id, country_code, rate, status, create_time FROM mo_tariff_config ORDER BY create_time DESC LIMIT 10")
    print('--- mo_tariff_config latest 10 ---')
    for row in cur.fetchall():
        print(row)

    cur.execute("SELECT id, type, value, reason, status, create_time FROM mo_blacklist ORDER BY create_time DESC LIMIT 5")
    print('\n--- mo_blacklist latest 5 ---')
    for row in cur.fetchall():
        print(row)

    cur.execute("SELECT id, word, category, status, create_time FROM mo_sensitive_word ORDER BY create_time DESC LIMIT 5")
    print('\n--- mo_sensitive_word latest 5 ---')
    for row in cur.fetchall():
        print(row)

    cur.execute("SELECT type, COUNT(*) FROM mo_points_log GROUP BY type")
    print('\n--- mo_points_log by type ---')
    for row in cur.fetchall():
        print(row)

    cur.execute("SELECT id, name, type, threshold, enabled, create_time FROM mo_risk_alert_config ORDER BY create_time DESC LIMIT 5")
    print('\n--- mo_risk_alert_config latest 5 ---')
    for row in cur.fetchall():
        print(row)

    cur.execute("SELECT id, from_warehouse_id, to_warehouse_id, quantity, reason, status, create_time FROM mo_inventory_transfer ORDER BY create_time DESC LIMIT 5")
    print('\n--- mo_inventory_transfer latest 5 ---')
    for row in cur.fetchall():
        print(row)
except Exception as e:
    print(f'Error: {e}')
