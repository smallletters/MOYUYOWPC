import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()
cur.execute("DELETE FROM schema_history WHERE version='20260825.03'")
conn.commit()
print("Deleted", cur.rowcount)
conn.close()