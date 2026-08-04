import pymysql
for pwd in ['', 'root', 'moyuyo123', 'password', '123456', 'moyuyo', 'moyuyo_root']:
    try:
        conn = pymysql.connect(host='localhost', port=3306, user='root', password=pwd, database='moyuyo_dev', charset='utf8mb4', connect_timeout=3)
        print(f'OK: password = "{pwd}"')
        conn.close()
        break
    except Exception as e:
        print(f'FAIL: "{pwd}" - {str(e)[:60]}')
