import pymysql
try:
    conn = pymysql.connect(
        host='127.0.0.1', port=3306, user='root', password='root',
        database='moyuyo', charset='utf8mb4'
    )
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

    # mo_points_log: 聚合
    cur.execute("SELECT type, COUNT(*) FROM mo_points_log GROUP BY type")
    print('\n--- mo_points_log by type ---')
    for row in cur.fetchall():
        print(row)
except Exception as e:
    print(f'Error: {e}')
