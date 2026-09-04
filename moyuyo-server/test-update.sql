USE moyuyo_dev;
-- 看下当前数据
SELECT id, main_image, update_time FROM mo_product WHERE id = 183000010;
SELECT id, product_id, url, sort FROM mo_product_image WHERE product_id = 183000010 ORDER BY sort;