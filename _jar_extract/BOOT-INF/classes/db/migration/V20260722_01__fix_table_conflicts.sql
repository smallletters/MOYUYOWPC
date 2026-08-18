-- ============================================================
-- V20260722_01__fix_table_conflicts.sql
-- 修复同名表结构冲突：
-- 1. mo_data_export_request：V1（GDPR导出）与 V20260718_01（管理导入导出）同名冲突
-- 2. mo_feedback：V7（意见反馈）与 V20260716_01（意见反馈）同名冲突
-- ============================================================

-- 1. mo_data_export_request：补充 admin 版本需要的 request_type 和 remark 列
-- V1 版本表结构 (GDPR)：id, user_id, export_id, status, download_url, download_expire_at, create_time, complete_time
-- V20260718_01 版本需要：id, user_id, export_id, request_type, status, download_url, remark, create_time, complete_time

-- 检查并添加 request_type 列
SET @db_name = DATABASE();
SET @exist_request_type = (SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'request_type');
SET @sql_request_type = IF(@exist_request_type = 0,
    'ALTER TABLE `mo_data_export_request` ADD COLUMN `request_type` VARCHAR(32) DEFAULT NULL COMMENT "导入/导出类型" AFTER `export_id`',
    'SELECT "request_type already exists"');
PREPARE stmt_request_type FROM @sql_request_type;
EXECUTE stmt_request_type;
DEALLOCATE PREPARE stmt_request_type;

-- 检查并添加 remark 列
SET @exist_remark = (SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_data_export_request' AND COLUMN_NAME = 'remark');
SET @sql_remark = IF(@exist_remark = 0,
    'ALTER TABLE `mo_data_export_request` ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT "备注" AFTER `download_url`',
    'SELECT "remark already exists"');
PREPARE stmt_remark FROM @sql_remark;
EXECUTE stmt_remark;
DEALLOCATE PREPARE stmt_remark;


-- 2. mo_feedback：补充 V20260716_01 版本需要的 reply_content 和 reply_time 列
-- V7 版本表结构：id, user_id, type, content, images, contact, status, create_time, update_time
-- V20260716_01 版本需要额外的：reply_content, reply_time

-- 检查并添加 reply_content 列
SET @exist_reply_content = (SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_feedback' AND COLUMN_NAME = 'reply_content');
SET @sql_reply_content = IF(@exist_reply_content = 0,
    'ALTER TABLE `mo_feedback` ADD COLUMN `reply_content` TEXT COMMENT "回复内容" AFTER `status`',
    'SELECT "reply_content already exists"');
PREPARE stmt_reply_content FROM @sql_reply_content;
EXECUTE stmt_reply_content;
DEALLOCATE PREPARE stmt_reply_content;

-- 检查并添加 reply_time 列
SET @exist_reply_time = (SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'mo_feedback' AND COLUMN_NAME = 'reply_time');
SET @sql_reply_time = IF(@exist_reply_time = 0,
    'ALTER TABLE `mo_feedback` ADD COLUMN `reply_time` DATETIME DEFAULT NULL COMMENT "回复时间" AFTER `reply_content`',
    'SELECT "reply_time already exists"');
PREPARE stmt_reply_time FROM @sql_reply_time;
EXECUTE stmt_reply_time;
DEALLOCATE PREPARE stmt_reply_time;
