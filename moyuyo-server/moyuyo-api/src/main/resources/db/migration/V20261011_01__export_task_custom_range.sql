-- ============================================================
-- V20261011_01__export_task_custom_range.sql
-- 为 mo_data_export_request 表增加自定义日期范围字段
-- 与 OrderExport.vue 新建任务弹窗"自定义"订单范围配套：
--   - start_date：自定义范围开始日期（含）
--   - end_date  ：自定义范围结束日期（含）
-- 范围仅在 order_scope='自定义' 时生效；其它值保持为空。
-- ============================================================

SET @db_name = DATABASE();

-- 添加 start_date 字段
SET @sql_start = (
    SELECT IF(
        COUNT(*) = 0,
        CONCAT('ALTER TABLE `mo_data_export_request` ADD COLUMN `start_date` DATE DEFAULT NULL COMMENT "自定义开始日期（order_scope=自定义 时生效）" AFTER `format`'),
        'SELECT "start_date 列已存在"'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'start_date'
);
PREPARE stmt FROM @sql_start;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 end_date 字段
SET @sql_end = (
    SELECT IF(
        COUNT(*) = 0,
        CONCAT('ALTER TABLE `mo_data_export_request` ADD COLUMN `end_date` DATE DEFAULT NULL COMMENT "自定义结束日期（order_scope=自定义 时生效）" AFTER `start_date`'),
        'SELECT "end_date 列已存在"'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'end_date'
);
PREPARE stmt FROM @sql_end;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;