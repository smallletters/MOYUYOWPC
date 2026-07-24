-- ============================================================
-- V20260722_04__fix_missing_tables.sql
-- 创建真正缺失的数据库表 + 为已有表补充示例数据
-- 注意：mo_ab_test/mo_coupon/mo_user_coupon/mo_points_log/
--       mo_order_tag/mo_risk_alert_config/mo_inventory_check/
--       mo_crowdfunding 已在早期迁移中创建，此处不再重复建表
-- ============================================================

-- ============================================================
-- 1. mo_sensitive_word - 敏感词表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_sensitive_word` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `word` VARCHAR(255) NOT NULL COMMENT '敏感词',
    `replacement` VARCHAR(255) DEFAULT '***' COMMENT '替换词',
    `match_mode` VARCHAR(16) DEFAULT 'EXACT' COMMENT '匹配模式：EXACT精确/FUZZY模糊/REGEX正则',
    `category` VARCHAR(64) COMMENT '分类',
    `hit_count` INT DEFAULT 0 COMMENT '命中次数',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_word` (`word`),
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词表';

INSERT IGNORE INTO `mo_sensitive_word` (`id`, `word`, `replacement`, `match_mode`, `category`, `status`) VALUES
(1, '违禁品', '***', 'EXACT', '违禁词', 'ENABLED'),
(2, '假货', '***', 'FUZZY', '投诉词', 'ENABLED');

-- ============================================================
-- 2. mo_flash_sale - 秒杀活动表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_flash_sale` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `product_id` BIGINT NOT NULL COMMENT '关联商品ID',
    `sku_id` BIGINT COMMENT '关联SKU ID',
    `flash_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
    `total_stock` INT NOT NULL DEFAULT 0 COMMENT '总库存',
    `sold_stock` INT DEFAULT 0 COMMENT '已售库存',
    `limit_per_user` INT DEFAULT 1 COMMENT '每人限购数量',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `active` TINYINT(1) DEFAULT 0 COMMENT '是否启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_active` (`active`),
    INDEX `idx_start_end` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

INSERT IGNORE INTO `mo_flash_sale` (`id`, `name`, `product_id`, `sku_id`, `flash_price`, `original_price`, `total_stock`, `sold_stock`, `limit_per_user`, `start_time`, `end_time`, `active`) VALUES
(1, '618宠物零食大促', 1, 1, 29.90, 59.90, 500, 120, 2, '2026-07-01 00:00:00', '2026-07-31 23:59:59', 1);

-- ============================================================
-- 3. mo_blacklist - 黑名单表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_blacklist` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `type` VARCHAR(16) NOT NULL COMMENT '类型：USER/IP/DEVICE/ADDRESS',
    `value` VARCHAR(255) NOT NULL COMMENT '值',
    `reason` VARCHAR(255) COMMENT '拉黑原因',
    `operator_id` BIGINT COMMENT '操作人ID',
    `expire_time` DATETIME COMMENT '过期时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_type_value` (`type`, `value`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表';

INSERT IGNORE INTO `mo_blacklist` (`id`, `type`, `value`, `reason`, `operator_id`, `status`) VALUES
(1, 'IP', '192.168.1.100', '恶意刷单', 1, 'ENABLED');

-- ============================================================
-- 4. mo_tariff_config - 关税配置表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_tariff_config` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `country_code` VARCHAR(8) NOT NULL COMMENT '国家代码',
    `product_category` VARCHAR(64) COMMENT '商品类别',
    `rate` DECIMAL(5,2) NOT NULL COMMENT '税率（百分比）',
    `currency` VARCHAR(8) NOT NULL DEFAULT 'USD' COMMENT '币种',
    `min_threshold` DECIMAL(10,2) COMMENT '最低阈值',
    `max_threshold` DECIMAL(10,2) COMMENT '最高阈值',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_country` (`country_code`),
    INDEX `idx_category` (`product_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关税配置表';

INSERT IGNORE INTO `mo_tariff_config` (`id`, `country_code`, `product_category`, `rate`, `currency`, `min_threshold`, `max_threshold`, `status`) VALUES
(1, 'US', '宠物食品', 10.00, 'USD', 0.00, 5000.00, 'ENABLED');

-- ============================================================
-- 5. mo_settlement - 结算表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_settlement` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `settlement_no` VARCHAR(64) NOT NULL COMMENT '结算单号',
    `pay_channel` VARCHAR(32) COMMENT '支付渠道',
    `period` VARCHAR(16) COMMENT '结算周期',
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '结算金额',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `remark` VARCHAR(255) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `settle_time` DATETIME COMMENT '结算时间',
    INDEX `idx_settlement_no` (`settlement_no`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算表';

INSERT IGNORE INTO `mo_settlement` (`id`, `settlement_no`, `pay_channel`, `period`, `amount`, `status`, `create_time`) VALUES
(1, 'SET20260701001', 'STRIPE', '2026-07', 15800.00, 'SETTLED', '2026-07-15 00:00:00');

-- ============================================================
-- 6. mo_finance_record - 财务记录表（全新表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `mo_finance_record` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `type` VARCHAR(16) NOT NULL COMMENT '类型：PAYMENT/REFUND/SETTLEMENT',
    `channel` VARCHAR(32) COMMENT '支付渠道',
    `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order_no` (`order_no`),
    INDEX `idx_type` (`type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务记录表';

INSERT IGNORE INTO `mo_finance_record` (`id`, `order_no`, `type`, `channel`, `amount`, `status`, `create_time`) VALUES
(1, 'ORD20260701001', 'PAYMENT', 'STRIPE', 299.00, 'SUCCESS', '2026-07-01 10:00:00');

-- ============================================================
-- 以下表已在早期迁移中创建，此处不重复插入数据
-- mo_coupon (V2), mo_user_coupon (V2), mo_ab_test (V20260718_03),
-- mo_order_tag (V20260720_01), mo_risk_alert_config (V20260720_01),
-- mo_inventory_check (V20260718_07), mo_crowdfunding (V20260720_01)
-- 这些表已在原始迁移中插入了数据
-- ============================================================
