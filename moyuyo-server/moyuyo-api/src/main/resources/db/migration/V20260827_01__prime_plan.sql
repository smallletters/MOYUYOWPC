-- ============================================================
-- V20260827_01__prime_plan.sql
-- Prime 会员套餐表（重构）
-- 配套 C 端 Prime 页面（pages/user/prime-page）：
--   - 月付 $9.99（MONTHLY）
--   - 年付 $99  推荐（YEARLY，节省 $20.88）
-- ============================================================
-- 兼容策略：
--   1) 旧 V7__init_extra_tables.sql 中已创建过 mo_prime_plan（字段为 name/level/...），
--      本次需 DROP 后重建以添加 code/recommend 字段
--   2) benefits 用 LONGTEXT 存 JSON 字符串，兼容 MySQL 5.7+/MariaDB 10.2+（不依赖 JSON 类型）

DROP TABLE IF EXISTS `mo_prime_plan`;

CREATE TABLE `mo_prime_plan` (
    `id`              BIGINT         NOT NULL,
    `code`            VARCHAR(32)    NOT NULL                COMMENT '套餐编码 MONTHLY / YEARLY',
    `name`            VARCHAR(64)    NOT NULL                COMMENT '套餐名称（月付/年付）',
    `duration_months` INT            NOT NULL DEFAULT 1     COMMENT '有效期月数',
    `price`           DECIMAL(10,2)  NOT NULL                COMMENT '现价（元）',
    `original_price`  DECIMAL(10,2)  NULL                    COMMENT '原价（元），用于展示划线价',
    `benefits`        LONGTEXT       NULL                    COMMENT '权益列表（JSON 字符串）',
    `recommend`       TINYINT(1)     NOT NULL DEFAULT 0     COMMENT '是否推荐 0否1是',
    `active`          TINYINT(1)     NOT NULL DEFAULT 1     COMMENT '是否启用 0否1是',
    `sort_order`      INT            NOT NULL DEFAULT 0     COMMENT '排序（升序）',
    `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_active_sort` (`active`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prime 会员套餐表';

-- 初始化月付 / 年付两个套餐（benefits 为 JSON 字符串，前端解析）
INSERT INTO `mo_prime_plan`
    (`id`, `code`, `name`, `duration_months`, `price`, `original_price`, `benefits`, `recommend`, `active`, `sort_order`)
VALUES
    (1, 'MONTHLY', '月付', 1,  9.99,  9.99,  '["全场免运费","专属会员价 5-10% off","优先发货 24h","免费退换货","专属客服","Prime Day 大促","每月赠送 $10 积分","新品优先购","Pet Hub 全场景解锁"]', 0, 1, 10),
    (2, 'YEARLY',  '年付', 12, 99.00, 119.88, '["全场免运费","专属会员价 5-10% off","优先发货 24h","免费退换货","专属客服","Prime Day 大促","每月赠送 $10 积分","新品优先购","Pet Hub 全场景解锁"]', 1, 1, 20);