USE moyuyo_dev;
-- 把 WC 同步来的5个一级分类 sort 改大值，让它们排到所有原生一级分类之后
UPDATE mo_category SET sort = 99 WHERE id = 21;  -- Harnesses
UPDATE mo_category SET sort = 100 WHERE id = 25; -- Pet Grooming
UPDATE mo_category SET sort = 101 WHERE id = 26; -- Pet Travel
UPDATE mo_category SET sort = 102 WHERE id = 33; -- Dogs
UPDATE mo_category SET sort = 103 WHERE id = 34; -- Cats

-- 验证最终一级分类排序
SELECT id, name, sort FROM mo_category WHERE level = 1 ORDER BY sort, id;