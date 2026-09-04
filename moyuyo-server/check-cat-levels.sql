USE moyuyo_dev;
-- 查看一级分类 + 二级分类数量
SELECT id, parent_id, name, level, (SELECT COUNT(*) FROM mo_category c2 WHERE c2.parent_id = c1.id) AS child_count
FROM mo_category c1
WHERE level = 1
ORDER BY id;
-- 总览
SELECT level, COUNT(*) AS cnt FROM mo_category GROUP BY level;