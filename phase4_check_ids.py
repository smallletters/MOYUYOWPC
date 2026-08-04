"""查询数据库中真实存在的订单和商品 ID"""
import pymysql
try:
    conn = pymysql.connect(
        host='127.0.0.1', port=3306, user='root', password='root',
        database='moyuyo', charset='utf8mb4'
    )
    cur = conn.cursor()
    cur.execute("SELECT id, order_no, status FROM mo_order LIMIT 5")
    print('--- mo_order 前 5 条 ---')
    for row in cur.fetchall():
        print(row)
    cur.execute("SELECT id, name, on_sale FROM mo_product LIMIT 5")
    print('\n--- mo_product 前 5 条 ---')
    for row in cur.fetchall():
        print(row)
except Exception as e:
    print(f'Error: {e}')
