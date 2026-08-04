# -*- coding: utf-8 -*-
"""清理本次验收产生的测试数据"""
import pymysql

conn = pymysql.connect(host='localhost', port=3306, user='root', password='',
                       database='moyuyo_dev', charset='utf8mb4')
cur = conn.cursor()

# 1. 风控规则测试数据
cur.execute("SELECT id, rule_name FROM mo_risk_rule WHERE rule_name LIKE '%验收规则%' OR rule_name LIKE 'API验收规则%'")
rows = cur.fetchall()
print('待清理风控规则:', rows)
for r in rows:
    cur.execute("DELETE FROM mo_risk_rule WHERE id=%s", (r[0],))

# 2. 关税配置测试数据（复测用，含 验收测试品类）
cur.execute("SELECT id, product_category, country_code FROM mo_tariff_config WHERE product_category LIKE '%验收%'")
rows = cur.fetchall()
print('待清理关税配置:', rows)
for r in rows:
    cur.execute("DELETE FROM mo_tariff_config WHERE id=%s", (r[0],))

# 3. 订单运营测试数据（拦截/改价/打印 复测记录，reason 含 复测）
for table, col in [('mo_order_intercept', 'reason'), ('mo_order_price_modify', 'reason')]:
    try:
        cur.execute(f"SELECT id FROM {table} WHERE {col} LIKE '%复测%'")
        ids = [r[0] for r in cur.fetchall()]
        if ids:
            print(f'{table} 清理 {len(ids)} 条')
            for i in ids:
                cur.execute(f"DELETE FROM {table} WHERE id=%s", (i,))
    except Exception as e:
        print(f'{table} 跳过: {e}')
try:
    cur.execute("SELECT id FROM mo_order_print WHERE template_name LIKE '%默认%' AND operator='admin' AND create_time > '2026-08-01 03:00:00'")
    ids = [r[0] for r in cur.fetchall()]
    if ids:
        print(f'mo_order_print 清理 {len(ids)} 条')
        for i in ids:
            cur.execute("DELETE FROM mo_order_print WHERE id=%s", (i,))
except Exception as e:
    print(f'mo_order_print 跳过: {e}')

conn.commit()
conn.close()
print('清理完成')
