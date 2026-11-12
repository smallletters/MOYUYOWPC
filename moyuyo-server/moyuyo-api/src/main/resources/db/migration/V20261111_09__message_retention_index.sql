-- ============================================================
-- V20261111_09__message_retention_index.sql
-- 私信 / 客服消息保留期清理所需索引
--
-- 背景：
--   云端消息只保留固定天数（默认 30 天），由 MessageRetentionJob 每天分批
--   执行 DELETE ... WHERE create_time < cutoff。
--   现有索引 mo_dm_message 为 (conversation_id, create_time)，
--   不能直接支撑按 create_time 单列的范围删除，会退化为全表扫描。
-- ============================================================

-- 1. 私信消息表：按时间清理
SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mo_dm_message'
    AND INDEX_NAME = 'idx_dm_msg_create_time'
);
SET @sql := IF(@idx = 0,
  'ALTER TABLE `mo_dm_message` ADD KEY `idx_dm_msg_create_time` (`create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 用户级消息删除标记表：随消息一同按时间清理
SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mo_dm_message_delete'
    AND INDEX_NAME = 'idx_dm_msg_delete_create_time'
);
SET @sql := IF(@idx = 0,
  'ALTER TABLE `mo_dm_message_delete` ADD KEY `idx_dm_msg_delete_create_time` (`create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 客服消息表：按时间清理
SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mo_cs_message'
    AND INDEX_NAME = 'idx_cs_msg_create_time'
);
SET @sql := IF(@idx = 0,
  'ALTER TABLE `mo_cs_message` ADD KEY `idx_cs_msg_create_time` (`create_time`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
