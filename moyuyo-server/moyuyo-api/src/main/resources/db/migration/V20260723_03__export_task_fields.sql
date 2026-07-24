-- ============================================================
-- V20260723_03__export_task_fields.sql
-- 为 mo_data_export_request 表增加导出任务相关字段
-- ============================================================

SET @db_name = DATABASE();

-- 添加 task_name 字段
SET @sql_task_name = (
    SELECT IF(
        COUNT(*) = 0,
        CONCAT('ALTER TABLE `mo_data_export_request` ADD COLUMN `task_name` VARCHAR(128) DEFAULT NULL COMMENT "任务名称" AFTER `export_id`'),
        'SELECT "task_name 列已存在"'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'task_name'
);
PREPARE stmt FROM @sql_task_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 order_scope 字段
SET @sql_order_scope = (
    SELECT IF(
        COUNT(*) = 0,
        CONCAT('ALTER TABLE `mo_data_export_request` ADD COLUMN `order_scope` VARCHAR(64) DEFAULT NULL COMMENT "订单范围" AFTER `task_name`'),
        'SELECT "order_scope 列已存在"'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'order_scope'
);
PREPARE stmt FROM @sql_order_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 format 字段
SET @sql_format = (
    SELECT IF(
        COUNT(*) = 0,
        CONCAT('ALTER TABLE `mo_data_export_request` ADD COLUMN `format` VARCHAR(16) DEFAULT NULL COMMENT "导出格式：Excel/CSV" AFTER `order_scope`'),
        'SELECT "format 列已存在"'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'format'
);
PREPARE stmt FROM @sql_format;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
