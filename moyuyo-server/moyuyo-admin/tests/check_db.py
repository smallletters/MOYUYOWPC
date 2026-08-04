import pymysql
try:
    conn = pymysql.connect(
        host='127.0.0.1', port=3306, user='root', password='root',
        database='moyuyo', charset='utf8mb4'
    )
    cur = conn.cursor()
    cur.execute("SHOW CREATE TABLE mo_ticket")
    row = cur.fetchone()
    print('--- mo_ticket DDL ---')
    print(row[1][:2000] if row else 'no data')

    cur.execute("SELECT id, ticket_no, status, type, priority FROM mo_ticket LIMIT 10")
    print('\n--- mo_ticket data ---')
    for row in cur.fetchall():
        print(row)
except Exception as e:
    print(f'Error: {e}')
