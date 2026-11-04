-- ============================================================
-- V20261103_04__dm_message_recall.sql
-- 私信消息撤回
--
-- 说明：
--   1. recalled 标记消息是否已撤回；撤回后 content 会被清空
--   2. recall_time 记录撤回时间，便于审计与后续产品化
-- ============================================================

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_dm_message' AND COLUMN_NAME = 'recalled');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_dm_message` ADD COLUMN `recalled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否已撤回：0否 1是'' AFTER `read_flag`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_dm_message' AND COLUMN_NAME = 'recall_time');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_dm_message` ADD COLUMN `recall_time` DATETIME NULL COMMENT ''撤回时间'' AFTER `recalled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
