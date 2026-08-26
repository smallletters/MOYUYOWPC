import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3306, user='root', password='dev123456', database='moyuyo_dev')
cur = conn.cursor()

# 直接执行完整 SQL（多条 INSERT 一次执行）
sql_full = """
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (105, 1, 'Dry Clean Spray', '🧴', 5, 2, NOW()),
  (106, 1, 'Eye Care', '👁️', 6, 2, NOW()),
  (107, 1, 'Paw Balm', '🐾', 7, 2, NOW()),
  (204, 2, 'Tops', '👕', 4, 2, NOW()),
  (205, 2, 'Bottoms', '👖', 5, 2, NOW()),
  (206, 2, 'Scarves & Accessories', '🧣', 6, 2, NOW()),
  (301, 3, 'Beds', '🛏️', 1, 2, NOW()),
  (302, 3, 'Mats', '🟦', 2, 2, NOW()),
  (303, 3, 'Bowls', '🥣', 3, 2, NOW()),
  (304, 3, 'Home Decor', '🏠', 4, 2, NOW()),
  (401, 4, 'Plush Toys', '🧸', 1, 2, NOW()),
  (402, 4, 'Chew Toys', '🦷', 2, 2, NOW()),
  (403, 4, 'Puzzle Toys', '🧩', 3, 2, NOW()),
  (404, 4, 'Training', '🎯', 4, 2, NOW()),
  (501, 5, 'Main Food', '🍖', 1, 2, NOW()),
  (502, 5, 'Treats', '🍗', 2, 2, NOW()),
  (503, 5, 'Water Fountains', '💧', 3, 2, NOW()),
  (504, 5, 'Feeding Tools', '🥄', 4, 2, NOW()),
  (601, 6, 'Leashes', '🦮', 1, 2, NOW()),
  (602, 6, 'Harnesses', '🎽', 2, 2, NOW()),
  (603, 6, 'Pet Apparel', '👔', 3, 2, NOW()),
  (604, 6, 'Travel Gear', '🧳', 4, 2, NOW()),
  (605, 6, 'Outdoor Sports', '⛰️', 5, 2, NOW()),
  (606, 6, 'Waterproof', '🌧️', 6, 2, NOW()),
  (607, 6, 'Seasonal', '🍂', 7, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort), icon=VALUES(icon);
"""
cur.execute(sql_full)
print("INSERT affected rows:", cur.rowcount)
conn.commit()

cur.execute("SELECT level, COUNT(*) FROM mo_category GROUP BY level")
print("Category counts by level:", cur.fetchall())
cur.execute("SELECT parent_id, COUNT(*) FROM mo_category WHERE level=2 GROUP BY parent_id ORDER BY parent_id")
print("Sub-categories per parent:", cur.fetchall())
conn.close()