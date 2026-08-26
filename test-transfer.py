import pymysql, requests
conn=pymysql.connect(host='127.0.0.1',port=3306,user='root',password='dev123456',database='moyuyo_dev')
cur=conn.cursor()
# 用一个还没领过的 coupon id 192000001
cur.execute("SELECT COALESCE(MAX(id),0)+1 FROM mo_user_coupon")
nid = cur.fetchone()[0]
cur.execute("INSERT INTO mo_user_coupon (id, user_id, coupon_id, status, create_time) VALUES (%s, %s, %s, 'UNUSED', NOW())", (nid, 200000001, 192000001))
conn.commit()
uc_id = nid
print('Inserted user_coupon id:', uc_id)

r = requests.post(f'http://localhost:8080/api/v1/auth/login', json={'email':'test@moyuyo.com','password':'012345678910'})
token = r.json()['data']['accessToken']
H = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}

r = requests.get(f'http://localhost:8080/api/v1/coupons/user-coupon/{uc_id}', headers=H)
print('detail:', r.status_code, r.text[:200])

r = requests.post(f'http://localhost:8080/api/v1/coupons/{uc_id}/transfer?toUserId=180000002', headers=H)
print('transfer:', r.status_code, r.text[:200])

cur.execute("SELECT user_id FROM mo_user_coupon WHERE id=%s", (uc_id,))
print('After transfer owner:', cur.fetchone())
conn.close()