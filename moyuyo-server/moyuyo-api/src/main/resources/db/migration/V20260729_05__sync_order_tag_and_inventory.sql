-- ============================================================
-- V20260729_05__sync_order_tag_and_inventory.sql
-- 同步订单标签和库存调拨的字段，使实体类与数据库表保持一致
-- 修复前：
--   mo_order_tag: name, color, icon, type, auto_rule, sort_order, enabled
--   mo_inventory_transfer: sku_id, from_warehouse_id, to_warehouse_id, quantity, status, operator_id, approver_id, reason, create_time, complete_time
-- 修复后：
--   mo_order_tag: 增加 description 列
--   mo_inventory_transfer: 添加 update_time 列（如果不存在）
-- ============================================================

-- 1. 给订单标签表添加 description 字段
ALTER TABLE `mo_order_tag`
    ADD COLUMN `description` VARCHAR(255) DEFAULT NULL COMMENT '标签描述' AFTER `color`;

-- 1.1 给订单标签表添加 update_time 字段（OrderTagEntity 含该字段）
SET @cnt := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'mo_order_tag'
               AND COLUMN_NAME = 'update_time');
SET @sql := IF(@cnt = 0,
    'ALTER TABLE `mo_order_tag` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 给库存调拨表添加 update_time（如果已存在则忽略）
SET @cnt := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'mo_inventory_transfer'
               AND COLUMN_NAME = 'update_time');
SET @sql := IF(@cnt = 0,
    'ALTER TABLE `mo_inventory_transfer` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 补充 mo_order_tag_rel 表的字段（确保 tag_id 字段存在）
SET @cnt := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'mo_order_tag_rel'
               AND COLUMN_NAME = 'tag_id');
SET @sql := IF(@cnt = 0,
    'ALTER TABLE `mo_order_tag_rel` ADD COLUMN `tag_id` BIGINT NOT NULL AFTER `order_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
