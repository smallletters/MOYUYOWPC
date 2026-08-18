-- ============================================================
-- V20260727_03__fix_cs_session_columns.sql
-- 补齐客服会话表 mo_cs_session 缺失的列：
--   - session_id    会话标识
--   - cs_staff_id   客服人员ID
-- 仅添加明确缺失的列（update_time 大概率已存在，故独立处理）
-- ============================================================

-- 判断列是否存在并条件性添加（MySQL 8.0+ 不支持 ADD COLUMN IF NOT EXISTS）
-- 用 SELECT 注入控制流的方式

-- 添加 session_id
SET @sql_add_session = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'session_id') = 0,
    'ALTER TABLE `mo_cs_session` ADD COLUMN `session_id` VARCHAR(64) NULL COMMENT ''会话标识'' AFTER `id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add_session; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 添加 cs_staff_id
SET @sql_add_cs_staff = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'cs_staff_id') = 0,
    'ALTER TABLE `mo_cs_session` ADD COLUMN `cs_staff_id` BIGINT NULL COMMENT ''客服人员ID'' AFTER `user_id`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add_cs_staff; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 添加 update_time（如缺失）
SET @sql_add_update_time = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'update_time') = 0,
    'ALTER TABLE `mo_cs_session` ADD COLUMN `update_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add_update_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;
