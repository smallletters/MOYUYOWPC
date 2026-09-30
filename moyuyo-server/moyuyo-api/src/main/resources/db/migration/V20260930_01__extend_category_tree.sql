-- ============================================================
-- V20260930_01__extend_category_tree.sql
-- 合并仓库根目录 fix-category-names.sql / update-categories.sql /
-- reorder-wc-categories.sql 三份历史修复脚本，统一进 Flyway，
-- 解决"docker volume 重建后新增二级分类与 WC 一级分类丢失"的问题。
--
-- 内容覆盖：
--   1) 一级分类 name 改成中文（id=1~6）
--   2) 修复 latin1 连接错误编码成 '?' 的中文二级分类名（id 105~107, 204~206, 301~304, 401~404, 501~504, 601~607）
--   3) 补充二级分类：108~112、207~210、305~308、405~408、505~509、608~611
--   4) 把 WC 同步来的5个一级分类 sort 改大值，让原生一级始终排前
--
-- 设计要点：
--   - 全部用 INSERT ... ON DUPLICATE KEY UPDATE，重跑幂等
--   - 所有 UPDATE 都带 WHERE id 限定，避免误改
--   - 文件末尾给一条验证 SELECT，运维脚本可以拿来对账
-- ============================================================

-- ============================================
-- 第一部分：把现有英文分类名改成中文
-- ============================================
UPDATE mo_category SET name='洗护美容' WHERE id=1;
UPDATE mo_category SET name='服饰'      WHERE id=2;
UPDATE mo_category SET name='窝床家具'  WHERE id=3;
UPDATE mo_category SET name='玩具'      WHERE id=4;
UPDATE mo_category SET name='喂食用具'  WHERE id=5;
UPDATE mo_category SET name='出行户外'  WHERE id=6;

-- ============================================
-- 第二部分：修复被 latin1 连接错误编码成 '?' 的中文分类名
-- ============================================
-- 洗护美容 105~107
UPDATE mo_category SET name='干洗喷雾', icon='🧴', sort=5 WHERE id=105;
UPDATE mo_category SET name='眼部清洁', icon='👁️', sort=6 WHERE id=106;
UPDATE mo_category SET name='护爪膏',   icon='🐾', sort=7 WHERE id=107;
-- 服饰 204~206
UPDATE mo_category SET name='服饰上衣', icon='👕', sort=4 WHERE id=204;
UPDATE mo_category SET name='服饰下装', icon='👖', sort=5 WHERE id=205;
UPDATE mo_category SET name='围巾配饰', icon='🧣', sort=6 WHERE id=206;
-- 窝床家具 301~304
UPDATE mo_category SET name='窝床',     icon='🛏️', sort=1 WHERE id=301;
UPDATE mo_category SET name='垫子',     icon='🟦', sort=2 WHERE id=302;
UPDATE mo_category SET name='餐具',     icon='🥣', sort=3 WHERE id=303;
UPDATE mo_category SET name='家居饰品', icon='🏠', sort=4 WHERE id=304;
-- 玩具 401~404
UPDATE mo_category SET name='啃咬玩具', icon='🦷', sort=1 WHERE id=401;
UPDATE mo_category SET name='益智玩具', icon='🧠', sort=2 WHERE id=402;
UPDATE mo_category SET name='训练玩具', icon='🎯', sort=3 WHERE id=403;
UPDATE mo_category SET name='毛绒玩具', icon='🧸', sort=4 WHERE id=404;
-- 喂食用具 501~504
UPDATE mo_category SET name='食盆',     icon='🥣', sort=1 WHERE id=501;
UPDATE mo_category SET name='饮水器',   icon='💧', sort=2 WHERE id=502;
UPDATE mo_category SET name='储粮桶',   icon='🪣', sort=3 WHERE id=503;
UPDATE mo_category SET name='喂食工具', icon='🍴', sort=4 WHERE id=504;
-- 出行户外 601~607
UPDATE mo_category SET name='牵引绳',   icon='🪢', sort=1 WHERE id=601;
UPDATE mo_category SET name='胸背带',   icon='🦺', sort=2 WHERE id=602;
UPDATE mo_category SET name='航空箱',   icon='🧳', sort=3 WHERE id=603;
UPDATE mo_category SET name='宠物推车', icon='🛒', sort=4 WHERE id=604;
UPDATE mo_category SET name='宠物背包', icon='🎒', sort=5 WHERE id=605;
UPDATE mo_category SET name='外出杯',   icon='🥤', sort=6 WHERE id=606;
UPDATE mo_category SET name='嘴套',     icon='😷', sort=7 WHERE id=607;

-- ============================================
-- 第三部分：补充二级分类
-- 编号约定：1xx 继续给洗护美容；2xx 给服饰；3xx 给窝床家具；
--          4xx 给玩具；5xx 给喂食用具；6xx 给出行户外。
-- 用 INSERT ... ON DUPLICATE KEY UPDATE 保证重跑幂等。
-- ============================================
-- 洗护美容 补充 (parent_id=1)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (108, 1, '免洗手套', '🧤', 8,  2, NOW()),
  (109, 1, '驱虫项圈', '🐛', 9,  2, NOW()),
  (110, 1, '牙刷套装', '🪥', 10, 2, NOW()),
  (111, 1, '洗澡刷',   '🛁', 11, 2, NOW()),
  (112, 1, '除蚤喷雾', '💨', 12, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 服饰 补充 (parent_id=2)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (207, 2, '雨衣',       '☔', 7,  2, NOW()),
  (208, 2, '防风外套',   '🧥', 8,  2, NOW()),
  (209, 2, '节日项圈',   '🎀', 9,  2, NOW()),
  (210, 2, '铃铛项圈',   '🔔', 10, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 窝床家具 补充 (parent_id=3)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (305, 3, '沙发窝',     '🛋️', 5, 2, NOW()),
  (306, 3, '凉席垫',     '❄️', 6, 2, NOW()),
  (307, 3, '宠物围栏',   '🚧', 7, 2, NOW()),
  (308, 3, '宠物门',     '🚪', 8, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 玩具 补充 (parent_id=4)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (405, 4, '飞盘玩具',   '🥏', 5, 2, NOW()),
  (406, 4, '绳结玩具',   '🪢', 6, 2, NOW()),
  (407, 4, '漏食球',     '⚽', 7, 2, NOW()),
  (408, 4, '逗猫棒',     '🎣', 8, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 喂食用具 补充 (parent_id=5)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (505, 5, '自动喂食器', '🤖', 5, 2, NOW()),
  (506, 5, '宠物餐桌',   '🍽️', 6, 2, NOW()),
  (507, 5, '防打翻碗',   '🥣', 7, 2, NOW()),
  (508, 5, '便携水壶',   '🚰', 8, 2, NOW()),
  (509, 5, '宠物冰垫',   '🧊', 9, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- 出行户外 补充 (parent_id=6)
INSERT INTO mo_category (id, parent_id, name, icon, sort, level, create_time) VALUES
  (608, 6, '宠物背包',       '🎒', 8,  2, NOW()),
  (609, 6, '车载宠物座椅',   '💺', 9,  2, NOW()),
  (610, 6, '车载安全带',     '🦺', 10, 2, NOW()),
  (611, 6, '防丢吊牌',       '🏷️', 11, 2, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), sort=VALUES(sort);

-- ============================================
-- 第四部分：把 WC 同步来的5个一级分类 sort 改大值，让它们排到原生一级之后
-- ============================================
UPDATE mo_category SET sort = 99  WHERE id = 21;  -- Harnesses
UPDATE mo_category SET sort = 100 WHERE id = 25;  -- Pet Grooming
UPDATE mo_category SET sort = 101 WHERE id = 26;  -- Pet Travel
UPDATE mo_category SET sort = 102 WHERE id = 33;  -- Dogs
UPDATE mo_category SET sort = 103 WHERE id = 34;  -- Cats

-- ============================================
-- 第五部分：验证最终一级分类 + 其二级分类数（运维对账用）
-- ============================================
SELECT
  c1.id,
  c1.parent_id,
  c1.name,
  c1.level,
  c1.sort,
  (SELECT COUNT(*) FROM mo_category c2 WHERE c2.parent_id = c1.id) AS child_count
FROM mo_category c1
WHERE c1.level = 1
ORDER BY c1.sort, c1.id;