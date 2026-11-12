-- 品牌名称唯一：编辑页支持手动输入品牌名，避免并发或重复输入产生同名品牌
-- 1) 历史重复品牌先归并到最小 id，避免加唯一索引失败
CREATE TEMPORARY TABLE tmp_brand_keep AS
SELECT name, MIN(id) AS keep_id
FROM mo_brand
GROUP BY name
HAVING COUNT(*) > 1;

-- 1.1) 商品品牌指向归并后的保留 id
UPDATE mo_product p
JOIN mo_brand b ON p.brand_id = b.id
JOIN tmp_brand_keep d ON b.name = d.name
SET p.brand_id = d.keep_id;

-- 1.2) 删除重复品牌行，只保留最小 id
DELETE b FROM mo_brand b
JOIN tmp_brand_keep d ON b.name = d.name AND b.id <> d.keep_id;

DROP TEMPORARY TABLE tmp_brand_keep;

-- 2) 品牌名称唯一索引（并发创建同名品牌时由数据库兜底）
ALTER TABLE mo_brand
  ADD UNIQUE KEY uk_brand_name (name);
