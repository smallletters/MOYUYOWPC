import pymysql
conn=pymysql.connect(host='127.0.0.1',port=3306,user='root',password='dev123456',database='moyuyo_dev')
cur=conn.cursor()
cur.execute("SELECT id FROM mo_user WHERE email='test@moyuyo.com'")
r = cur.fetchone()
print('test user id:', r)
cur.execute("SELECT id, user_id, coupon_id, status FROM mo_user_coupon WHERE user_id=%s LIMIT 5", (r[0],))
for uc in cur.fetchall():
    print('coupon:', uc)
conn.close()