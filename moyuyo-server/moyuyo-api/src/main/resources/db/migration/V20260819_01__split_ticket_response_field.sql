-- ============================================================
-- V20260819_01__split_ticket_response_field.sql
-- 拆分 mo_ticket.response_time 字段：
--   response_time      -> reply_content  (回复内容，原语义被前端误用为耗时)
--   新增 first_response_minutes (首响耗时，分钟)
-- ============================================================

-- 1) 将原 response_time 重命名为 reply_content（保留数据）
SET @sql_rename := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_ticket' AND COLUMN_NAME = 'response_time') > 0,
    'ALTER TABLE `mo_ticket` CHANGE COLUMN `response_time` `reply_content` TEXT COMMENT ''工单回复内容''',
    'SELECT 1'
));
PREPARE stmt FROM @sql_rename; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 新增 first_response_minutes（首响耗时，分钟）。如已存在则跳过
SET @sql_add := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_ticket' AND COLUMN_NAME = 'first_response_minutes') = 0,
    'ALTER TABLE `mo_ticket` ADD COLUMN `first_response_minutes` INT NULL COMMENT ''首响耗时(分钟)'' AFTER `reply_content`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_add; EXECUTE stmt; DEALLOCATE PREPARE stmt;