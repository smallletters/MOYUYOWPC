-- ============================================================
-- V20261102_03__member_price_config.sql
-- 会员价（按会员等级统一折扣）配置 + 订单会员优惠金额字段
-- 规则：
--   1) 五档等级：NORMAL / SILVER / GOLD / PLATINUM / DIAMOND
--   2) discount_rate 为折扣系数：1.00 = 原价，0.95 = 9.5 折
--   3) 折扣按商品销售价计算，与优惠券叠加，与秒杀不叠加
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_member_price_config` (
    `id`            BIGINT       NOT NULL,
    `level`         VARCHAR(32)  NOT NULL                COMMENT '会员等级 NORMAL/SILVER/GOLD/PLATINUM/DIAMOND',
    `discount_rate` DECIMAL(3,2) NOT NULL DEFAULT 1.00   COMMENT '折扣系数，1.00=原价，0.95=9.5折',
    `description`   VARCHAR(128) NULL                    COMMENT '等级说明',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用 0否1是',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级价格折扣配置';

-- 初始化五档默认折扣；已存在则不覆盖运营已改过的值
INSERT INTO `mo_member_price_config`
    (`id`, `level`, `discount_rate`, `description`, `enabled`)
VALUES
    (1, 'NORMAL',   1.00, '注册即获得',        1),
    (2, 'SILVER',   0.95, '累计消费满 500 元',   1),
    (3, 'GOLD',     0.88, '累计消费满 2000 元',  1),
    (4, 'PLATINUM', 0.85, '累计消费满 8000 元',  1),
    (5, 'DIAMOND',  0.80, '累计消费满 25000 元', 1)
ON DUPLICATE KEY UPDATE `level` = VALUES(`level`);

-- 订单表新增会员优惠金额字段（幂等：已存在则跳过）
SET @sql_member_discount := (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'member_discount') = 0,
    'ALTER TABLE `mo_order` ADD COLUMN `member_discount` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''会员等级优惠金额'' AFTER `points_discount`',
    'SELECT 1'
));
PREPARE stmt FROM @sql_member_discount; EXECUTE stmt; DEALLOCATE PREPARE stmt;
