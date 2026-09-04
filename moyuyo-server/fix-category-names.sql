USE moyuyo_dev;
-- 修复被 latin1 连接错误编码成 '?' 的中文分类名
-- 原始 SQL 文件 V20260826_01__seed_category_full.sql 中的中文名

UPDATE mo_category SET name='干洗喷雾', icon='🧴', sort=5 WHERE id=105;
UPDATE mo_category SET name='眼部清洁', icon='👁️', sort=6 WHERE id=106;
UPDATE mo_category SET name='护爪膏', icon='🐾', sort=7 WHERE id=107;
UPDATE mo_category SET name='服饰上衣', icon='👕', sort=4 WHERE id=204;
UPDATE mo_category SET name='服饰下装', icon='👖', sort=5 WHERE id=205;
UPDATE mo_category SET name='围巾配饰', icon='🧣', sort=6 WHERE id=206;
UPDATE mo_category SET name='窝床', icon='🛏️', sort=1 WHERE id=301;
UPDATE mo_category SET name='垫子', icon='🟦', sort=2 WHERE id=302;
UPDATE mo_category SET name='餐具', icon='🥣', sort=3 WHERE id=303;
UPDATE mo_category SET name='家居饰品', icon='🏠', sort=4 WHERE id=304;
-- Toys 子分类（401-404）
UPDATE mo_category SET name='啃咬玩具', icon='🦷', sort=1 WHERE id=401;
UPDATE mo_category SET name='益智玩具', icon='🧠', sort=2 WHERE id=402;
UPDATE mo_category SET name='训练玩具', icon='🎯', sort=3 WHERE id=403;
UPDATE mo_category SET name='毛绒玩具', icon='🧸', sort=4 WHERE id=404;
-- Feeding 子分类（501-504）
UPDATE mo_category SET name='食盆', icon='🥣', sort=1 WHERE id=501;
UPDATE mo_category SET name='饮水器', icon='💧', sort=2 WHERE id=502;
UPDATE mo_category SET name='储粮桶', icon='🪣', sort=3 WHERE id=503;
UPDATE mo_category SET name='喂食工具', icon='🍴', sort=4 WHERE id=504;
-- Travel&Outdoor 子分类（601-607）
UPDATE mo_category SET name='牵引绳', icon='🪢', sort=1 WHERE id=601;
UPDATE mo_category SET name='胸背带', icon='🦺', sort=2 WHERE id=602;
UPDATE mo_category SET name='航空箱', icon='🧳', sort=3 WHERE id=603;
UPDATE mo_category SET name='宠物推车', icon='🛒', sort=4 WHERE id=604;
UPDATE mo_category SET name='宠物背包', icon='🎒', sort=5 WHERE id=605;
UPDATE mo_category SET name='外出杯', icon='🥤', sort=6 WHERE id=606;
UPDATE mo_category SET name='嘴套', icon='😷', sort=7 WHERE id=607;

-- 验证
SELECT id, name, icon FROM mo_category WHERE id IN (105,106,107,204,205,206,301,302,303,304,401,402,403,404,501,502,503,504,601,602,603,604,605,606,607) ORDER BY id;