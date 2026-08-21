-- ============================================================
-- V20260820_02__cs_chat_messages.sql
-- 客服会话消息表 + 客服会话表字段补齐
--
-- 背景：
--   - mo_cs_session 已存在（V20260720_01），但只有会话元数据
--   - 没有消息记录表，客服在线聊天功能缺失
--   - 现补建 mo_cs_message，并同步规范 mo_cs_session 字段名与索引
-- ============================================================

-- 创建客服会话消息表
CREATE TABLE IF NOT EXISTS `mo_cs_message` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `session_id` BIGINT NOT NULL COMMENT '关联会话ID（mo_cs_session.id）',
    `sender_type` VARCHAR(16) NOT NULL COMMENT 'USER 用户 / AGENT 客服 / SYSTEM 系统',
    `sender_id` BIGINT COMMENT '发送方ID（用户ID或管理员ID）',
    `sender_name` VARCHAR(64) COMMENT '发送方展示名（冗余，便于列表渲染）',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `content_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT 文本 / IMAGE 图片 / SYSTEM 系统提示',
    `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已被客服读取：0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_cs_msg_session` (`session_id`, `create_time`),
    INDEX `idx_cs_msg_unread` (`session_id`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话消息表';

-- 同步：mo_cs_session 加 message_count 与 last_message_at 字段（如果在用旧版）
-- 老版本 message_count 已有，但新加 last_message_at 用于轮询增量
-- 用 ALTER + IF NOT EXISTS 兼容方式
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mo_cs_session'
    AND COLUMN_NAME = 'last_message_at'
);
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `last_message_at` DATETIME NULL COMMENT ''最后一条消息时间'' AFTER `message_count`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
