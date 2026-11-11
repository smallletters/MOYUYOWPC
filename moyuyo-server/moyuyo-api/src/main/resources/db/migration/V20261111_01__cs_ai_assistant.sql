-- ============================================================
-- V20261111_01__cs_ai_assistant.sql
-- 客服会话接入第三方 AI 智能体（扣子）所需字段
--
-- 背景：
--   现有客服回复为 CsService.autoReply() 的关键词规则，无法处理长尾问题。
--   接入扣子 OpenAPI 后需要两件事：
--     1) 关闭某会话的 AI 自动回复（转人工后不能再由机器人抢答）
--     2) 记住扣子的 conversation_id，复用多轮上下文（否则每轮都要重传历史，token 成本翻倍）
-- ============================================================

-- 1. 是否允许 AI 自动回复：1=允许（默认），0=已转人工/人工接管
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'ai_enabled');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `ai_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许AI自动回复：1允许 0已转人工'' AFTER `status`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 第三方智能体会话 ID（扣子 v3/chat 返回的 conversation_id）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'ai_conversation_id');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `ai_conversation_id` VARCHAR(64) NULL COMMENT ''第三方智能体会话ID（扣子 conversation_id）'' AFTER `ai_enabled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
