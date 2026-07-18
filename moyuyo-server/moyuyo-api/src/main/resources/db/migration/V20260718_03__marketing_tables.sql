-- ============================================================
-- V20260718_03__marketing_tables.sql
-- 营销活动 / A/B测试 / 用户行为 表
-- ============================================================

-- 1. 营销活动表（补充详细字段）
CREATE TABLE IF NOT EXISTS `mo_marketing_campaign` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/UPCOMING/ACTIVE/ENDED',
    `description` VARCHAR(500) COMMENT '活动描述',
    `start_date` DATETIME NOT NULL COMMENT '开始时间',
    `end_date` DATETIME NOT NULL COMMENT '结束时间',
    `participants` INT DEFAULT 0 COMMENT '参与人数',
    `gmv` DECIMAL(12,2) DEFAULT 0 COMMENT 'GMV金额',
    `budget` DECIMAL(12,2) DEFAULT 0 COMMENT '预算金额',
    `cost` DECIMAL(12,2) DEFAULT 0 COMMENT '实际成本',
    `order_increase` DECIMAL(5,1) DEFAULT 0 COMMENT '订单提升率(%)',
    `conversion_increase` DECIMAL(5,1) DEFAULT 0 COMMENT '转化提升率(%)',
    `avg_order_value` DECIMAL(10,2) DEFAULT 0 COMMENT '平均客单价',
    `roi` DECIMAL(5,1) DEFAULT 0 COMMENT 'ROI',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_date_range` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动表';

-- 2. A/B测试表
CREATE TABLE IF NOT EXISTS `mo_ab_test` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '测试名称',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/RUNNING/COMPLETED/ENDED',
    `description` VARCHAR(500) COMMENT '测试描述',
    `group_a_visitors` INT DEFAULT 0 COMMENT 'A组访客数',
    `group_b_visitors` INT DEFAULT 0 COMMENT 'B组访客数',
    `group_a_conv_rate` DECIMAL(5,1) DEFAULT 0 COMMENT 'A组转化率(%)',
    `group_b_conv_rate` DECIMAL(5,1) DEFAULT 0 COMMENT 'B组转化率(%)',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A/B测试表';

-- 3. 用户行为表
CREATE TABLE IF NOT EXISTS `mo_user_behavior` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `behavior_type` VARCHAR(32) NOT NULL COMMENT '行为类型：VIEW_PRODUCT/SEARCH/ADD_CART/PLACE_ORDER/FAVORITE',
    `count` INT DEFAULT 0 COMMENT '次数',
    `last_time` DATETIME COMMENT '最后发生时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_type` (`user_id`, `behavior_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为表';
