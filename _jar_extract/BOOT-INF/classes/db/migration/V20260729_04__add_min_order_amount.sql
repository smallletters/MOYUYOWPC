-- 修复 mo_coupon 表缺失 min_order_amount 列（与 CouponEntity 对齐）
SET @sql = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_coupon' AND COLUMN_NAME='min_order_amount')=0,
  'ALTER TABLE `mo_coupon` ADD COLUMN `min_order_amount` DECIMAL(10,2) DEFAULT 0', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
