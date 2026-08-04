-- 修复 mo_coupon 表 amount 字段 NOT NULL 无默认值的问题
-- AdminCouponServiceImpl.create 不写 amount 字段，但表 amount 字段 NOT NULL → INSERT 失败
-- 解决：给 amount 字段加默认值 0

SET @sql_amount_default = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_coupon' AND COLUMN_NAME = 'amount' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL) > 0,
    'ALTER TABLE `mo_coupon` MODIFY COLUMN `amount` DECIMAL(10,2) NOT NULL DEFAULT 0',
    'SELECT 1'
));
PREPARE stmt FROM @sql_amount_default; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容：给 start_time/end_time 设置默认值，方便未传时间的场景
-- 如果字段存在且为 NOT NULL 但无默认值，则修改为允许 NULL
SET @sql_start_nullable = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_coupon' AND COLUMN_NAME = 'start_time' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL) > 0,
    'ALTER TABLE `mo_coupon` MODIFY COLUMN `start_time` DATETIME NULL',
    'SELECT 1'
));
PREPARE stmt FROM @sql_start_nullable; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_end_nullable = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_coupon' AND COLUMN_NAME = 'end_time' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL) > 0,
    'ALTER TABLE `mo_coupon` MODIFY COLUMN `end_time` DATETIME NULL',
    'SELECT 1'
));
PREPARE stmt FROM @sql_end_nullable; EXECUTE stmt; DEALLOCATE PREPARE stmt;
