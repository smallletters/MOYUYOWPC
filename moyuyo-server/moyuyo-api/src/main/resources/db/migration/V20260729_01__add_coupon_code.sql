-- 修复 mo_coupon 表缺失 code 列（MySQL 5.5 不支持 IF NOT EXISTS，使用 INFORMATION_SCHEMA 判断）
SET @sql_add_code = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_coupon' AND COLUMN_NAME = 'code') = 0,
    'ALTER TABLE `mo_coupon` ADD COLUMN `code` VARCHAR(64) NULL AFTER `id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 强制使用 id 重新生成唯一 code（覆盖历史上残留的错误填充值）
UPDATE mo_coupon SET code = CONCAT('CP', id);

-- 添加唯一索引（若不存在）
SET @sql_add_uk = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_coupon' AND INDEX_NAME = 'uk_mo_coupon_code') = 0,
    'ALTER TABLE `mo_coupon` ADD UNIQUE INDEX `uk_mo_coupon_code` (`code`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add_uk; EXECUTE stmt; DEALLOCATE PREPARE stmt;
