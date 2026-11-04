-- 营销活动执行：满减门槛、指定商品/分类、用量与订单优惠字段

ALTER TABLE `mo_marketing_campaign`
    ADD COLUMN `min_order_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '满减门槛金额',
    ADD COLUMN `product_ids` VARCHAR(2000) DEFAULT NULL COMMENT '指定商品ID（逗号分隔）',
    ADD COLUMN `product_category_ids` VARCHAR(500) DEFAULT NULL COMMENT '指定分类ID（逗号分隔）',
    ADD COLUMN `used_count` INT DEFAULT 0 COMMENT '已占用活动件数',
    ADD COLUMN `used_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '已占用活动优惠金额';

CREATE TABLE IF NOT EXISTS `mo_campaign_usage` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `campaign_id` BIGINT NOT NULL COMMENT '活动ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `item_count` INT NOT NULL DEFAULT 0 COMMENT '占用件数',
    `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `status` VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED/CONFIRMED/RELEASED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_campaign_user` (`campaign_id`, `user_id`, `status`),
    INDEX `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动用量记录';

CREATE TABLE IF NOT EXISTS `mo_campaign_user_usage` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `campaign_id` BIGINT NOT NULL COMMENT '活动ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `used_count` INT NOT NULL DEFAULT 0 COMMENT '该用户已占用件数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_campaign_user` (`campaign_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动用户用量（原子限购）';

ALTER TABLE `mo_order`
    ADD COLUMN `campaign_discount` DECIMAL(12,2) DEFAULT 0 COMMENT '营销活动优惠金额';

ALTER TABLE `mo_order_item`
    ADD COLUMN `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '活动前原价',
    ADD COLUMN `campaign_id` BIGINT DEFAULT NULL COMMENT '命中的营销活动ID',
    ADD COLUMN `campaign_discount` DECIMAL(12,2) DEFAULT 0 COMMENT '该商品活动优惠金额';
