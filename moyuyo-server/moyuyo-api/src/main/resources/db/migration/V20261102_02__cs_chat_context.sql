-- ============================================================
-- V20261102_02__cs_chat_context.sql
-- 客服会话增加「咨询商品 / 咨询订单」上下文，消息支持商品/订单卡片
--
-- 背景：
--   用户在商详页或订单页进入客服时携带的商品/订单信息只存在于前端 URL 参数，
--   后台客服会话页拿不到商品图与订单，无法针对性回复或推荐商品。
--
-- 设计：
--   mo_cs_session 保存进入会话时携带的商品/订单上下文（冗余名称/图片，商品删除后仍可展示）
--   mo_cs_message 保存结构化卡片引用（ref_type / ref_id / ref_title / ref_snapshot）
--   ref_snapshot 为 JSON 快照，前端直接渲染商品/订单卡片，避免渲染时反复联表
-- ============================================================

-- 1. 会话：咨询商品上下文
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'product_id');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `product_id` BIGINT NULL COMMENT ''咨询商品ID'' AFTER `operator_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'product_name');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `product_name` VARCHAR(255) NULL COMMENT ''咨询商品名称（冗余）'' AFTER `product_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'product_image');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `product_image` VARCHAR(500) NULL COMMENT ''咨询商品主图（冗余）'' AFTER `product_name`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 会话：咨询订单上下文
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_session' AND COLUMN_NAME = 'order_no');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_session` ADD COLUMN `order_no` VARCHAR(64) NULL COMMENT ''咨询订单号'' AFTER `product_image`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 消息：商品/订单卡片引用字段
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_message' AND COLUMN_NAME = 'ref_type');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_message` ADD COLUMN `ref_type` VARCHAR(16) NULL COMMENT ''PRODUCT 商品 / ORDER 订单'' AFTER `content_type`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_message' AND COLUMN_NAME = 'ref_id');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_message` ADD COLUMN `ref_id` BIGINT NULL COMMENT ''引用对象ID（商品ID或订单ID）'' AFTER `ref_type`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_message' AND COLUMN_NAME = 'ref_title');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_message` ADD COLUMN `ref_title` VARCHAR(255) NULL COMMENT ''卡片标题（冗余，便于列表预览）'' AFTER `ref_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_cs_message' AND COLUMN_NAME = 'ref_snapshot');
SET @sql := IF(@col = 0,
  'ALTER TABLE `mo_cs_message` ADD COLUMN `ref_snapshot` TEXT NULL COMMENT ''卡片数据JSON快照'' AFTER `ref_title`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
