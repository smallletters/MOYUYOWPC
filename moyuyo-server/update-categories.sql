USE moyuyo_dev;

-- ============================================
-- 第一部分：把现有英文分类名改成中文
-- ============================================

-- 一级分类（id=1~6）
UPDATE mo_category SET name='洗护美容' WHERE id=1;
UPDATE mo_category SET name='服饰' WHERE id=2;
UPDATE mo_category SET name='窝床家具' WHERE id=3;
UPDATE mo_category SET name='玩具' WHERE id=4;
UPDATE mo_category SET name='喂食用具' WHERE id=5;
UPDATE mo_category SET name='出行户外' WHERE id=6;

-- 洗护美容下的英文二级 → 中文
UPDATE mo_category SET name='沐浴露' WHERE id=101;
UPDATE mo_category SET name='护毛素' WHERE id=102;
UPDATE mo_category SET name='耳部护理' WHERE id=103;
UPDATE mo_category SET name='口腔护理' WHERE id=104;

-- 服饰下的英文二级 → 中文
UPDATE mo_category SET name='冬装外套' WHERE id=201;
UPDATE mo_category SET name='T恤' WHERE id=202;
UPDATE mo_category SET name='主题服饰' WHERE id=203;

-- ============================================
-- 第二部分：补充二级分类
-- 编号约定：1xx 继续给洗护美容(已有 101-107)，新增 108-112
--          2xx 给服饰(已有 201-206)，新增 207-210
--          3xx 给窝床家具(已有 301-304)，新增 305-308
--          4xx 给玩具(已有 401-404)，新增 405-408
--          5xx 给喂食用具(已有 501-504)，新增 505-509
--          6xx 给出行户外(已有 601-607)，新增 608-611
-- ============================================

-- 洗护美容 补充 (parent_id=1)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (108, 1, '免洗手套', '🧤', 8, 2, NOW()),
  (109, 1, '驱虫项圈', '🐛', 9, 2, NOW()),
  (110, 1, '牙刷套装', '🪥', 10, 2, NOW()),
  (111, 1, '洗澡刷', '🛁', 11, 2, NOW()),
  (112, 1, '除蚤喷雾', '💨', 12, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 服饰 补充 (parent_id=2)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (207, 2, '雨衣', '☔', 7, 2, NOW()),
  (208, 2, '防风外套', '🧥', 8, 2, NOW()),
  (209, 2, '节日项圈', '🎀', 9, 2, NOW()),
  (210, 2, '铃铛项圈', '🔔', 10, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 窝床家具 补充 (parent_id=3)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (305, 3, '沙发窝', '🛋️', 5, 2, NOW()),
  (306, 3, '凉席垫', '❄️', 6, 2, NOW()),
  (307, 3, '宠物围栏', '🚧', 7, 2, NOW()),
  (308, 3, '宠物门', '🚪', 8, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 玩具 补充 (parent_id=4)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (405, 4, '飞盘玩具', '🥏', 5, 2, NOW()),
  (406, 4, '绳结玩具', '🪢', 6, 2, NOW()),
  (407, 4, '漏食球', '⚽', 7, 2, NOW()),
  (408, 4, '逗猫棒', '🎣', 8, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 喂食用具 补充 (parent_id=5)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (505, 5, '自动喂食器', '🤖', 5, 2, NOW()),
  (506, 5, '宠物餐桌', '🍽️', 6, 2, NOW()),
  (507, 5, '防打翻碗', '🥣', 7, 2, NOW()),
  (508, 5, '便携水壶', '🚰', 8, 2, NOW()),
  (509, 5, '宠物冰垫', '🧊', 9, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 出行户外 补充 (parent_id=6)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (608, 6, '宠物背包', '🎒', 8, 2, NOW()),
  (609, 6, '车载宠物座椅', '💺', 9, 2, NOW()),
  (610, 6, '车载安全带', '🦺', 10, 2, NOW()),
  (611, 6, '防丢吊牌', '🏷️', 11, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 验证：列出所有一级分类 + 其二级分类数
SELECT id, parent_id, name, level, (SELECT COUNT(*) FROM mo_category c2 WHERE c2.parent_id = c1.id) AS child_count
FROM mo_category c1
WHERE level = 1
ORDER BY id;