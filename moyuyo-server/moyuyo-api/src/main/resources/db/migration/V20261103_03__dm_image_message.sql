-- ============================================================
-- V20261103_03__dm_image_message.sql
-- 私信支持图片消息
--
-- 说明：
--   1. mo_dm_message.content_type 扩展为 TEXT / IMAGE，content 存图片 URL
--   2. mo_dm_conversation 新增 last_message_type，会话列表可按类型展示
--      「[图片]」而不是把 URL 当预览文本（便于前端按语言本地化）
-- ============================================================

-- 1. 消息类型注释更新（列本身已是 VARCHAR(16)，无需扩宽）
ALTER TABLE `mo_dm_message`
    MODIFY COLUMN `content_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT'
    COMMENT 'TEXT 文本 / IMAGE 图片';

-- 2. 会话最后一条消息类型
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_dm_conversation'
    AND COLUMN_NAME = 'last_message_type');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_dm_conversation` ADD COLUMN `last_message_type` VARCHAR(16) NULL COMMENT ''最后一条消息类型 TEXT/IMAGE'' AFTER `last_message_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
