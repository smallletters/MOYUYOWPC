-- ============================================================
-- V20261008_01__shipping_rate.sql
-- 配送方式字典 + 运费规则表 + 策略绑定关系
-- 目的：
--   1) 把 APP 结算页的硬编码运费（standard 0 / express 12）迁到后台可配置
--   2) 按 zone × method 维度计费，支持"按件/按重" + "满额免邮" + 优先级
--   3) 不替换 mo_shipping_strategy（策略做"用哪条规则"，规则做"怎么算"）
-- 关联：
--   - mo_shipping_rate.zone_id       -> mo_shipping_zone.id
--   - mo_shipping_rate.method_id     -> mo_shipping_method.id
--   - mo_shipping_strategy_method    -> mo_shipping_strategy.id × mo_shipping_method.id（多对多绑定）
--
-- 幂等说明：使用 CREATE TABLE IF NOT EXISTS / INSERT IGNORE 防止重跑冲突。
-- 第一次失败时 CREATE TABLE 已提交、INSERT 回滚，下次启动重跑不会报错。
-- ============================================================

-- 1) 配送方式字典（standard / express / same_day...）
CREATE TABLE IF NOT EXISTS `mo_shipping_method` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `code`          VARCHAR(32)  NOT NULL COMMENT '方式编码：standard/express/same_day/overnight',
    `name_en`       VARCHAR(64)  NOT NULL COMMENT '英文名',
    `name_zh`       VARCHAR(64)  NOT NULL COMMENT '中文名',
    `eta_min_days`  INT          NOT NULL DEFAULT 3 COMMENT '预计送达最小天数',
    `eta_max_days`  INT          NOT NULL DEFAULT 7 COMMENT '预计送达最大天数',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '展示排序，升序',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_method_code` (`code`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送方式字典';

-- 2) 运费规则表（区域 × 方式 -> 价格/计费方式）
-- 同 (zone_id, method_id) 只允许一条 ACTIVE 规则，由应用层唯一索引保证
CREATE TABLE IF NOT EXISTS `mo_shipping_rate` (
    `id`              BIGINT        NOT NULL COMMENT '主键',
    `zone_id`         BIGINT        NOT NULL COMMENT '关联 mo_shipping_zone.id',
    `method_id`       BIGINT        NOT NULL COMMENT '关联 mo_shipping_method.id',
    `charge_type`     TINYINT       NOT NULL COMMENT '1=按件 2=按重 3=按金额',
    `first_charge`    DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '首费（USD）',
    `first_unit`      INT           NOT NULL DEFAULT 1 COMMENT '首件/首重数量',
    `continue_charge` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '续费（USD）',
    `continue_unit`   INT           NOT NULL DEFAULT 1 COMMENT '续件/续重单位（件 or g）',
    `free_threshold`  DECIMAL(10,2)          DEFAULT NULL COMMENT '满额包邮门槛（商品金额，USD），NULL=不包邮',
    `currency`        VARCHAR(8)    NOT NULL DEFAULT 'USD' COMMENT '货币',
    `priority`        INT           NOT NULL DEFAULT 0 COMMENT '越小越优先',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `remark`          VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zone_method_active` (`zone_id`, `method_id`, `status`),
    KEY `idx_zone_status` (`zone_id`, `status`),
    KEY `idx_method_status` (`method_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费规则：区域+方式 → 价格';

-- 3) 策略与方式的多对多绑定（一个 zone/strategy 可同时支持多种配送方式）
CREATE TABLE IF NOT EXISTS `mo_shipping_strategy_method` (
    `strategy_id` BIGINT NOT NULL COMMENT '关联 mo_shipping_strategy.id',
    `method_id`   BIGINT NOT NULL COMMENT '关联 mo_shipping_method.id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`strategy_id`, `method_id`),
    KEY `idx_method_id` (`method_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货策略绑定的配送方式';

-- 4) 写入字典种子数据（4 种最常见方式）
--    使用 INSERT IGNORE 防止重跑冲突（id 是主键）
INSERT IGNORE INTO `mo_shipping_method` (`id`, `code`, `name_en`, `name_zh`, `eta_min_days`, `eta_max_days`, `sort_order`, `status`, `remark`) VALUES
    (200000001, 'standard',   'Standard Shipping', '标准配送', 5, 8, 10, 'ACTIVE', '默认最慢最便宜'),
    (200000002, 'express',    'Express Shipping',  '快递配送', 2, 4, 20, 'ACTIVE', '平衡速度与价格'),
    (200000003, 'priority',   'Priority Shipping', '优先配送', 1, 2, 30, 'ACTIVE', '次日达'),
    (200000004, 'same_day',   'Same Day Delivery', '当日达',   0, 1, 40, 'INACTIVE', '按需启用');

-- 5) 写入运费规则种子数据
--    北美(zone=1,US/CA)：标准 \$5.99/件, 续 \$2.5/件; 快递 \$12.99/件, 续 \$3.5/件; 满 \$59 免邮
--    西欧(zone=2,GB/DE/FR/IT/ES)：标准 €7.99/件, 续 €3/件; 快递 €15.99/件, 续 €4/件; 满 €69 免邮（暂用 USD 计价）
--    大洋洲(zone=3,AU/NZ)：标准 \$9.99/件, 续 \$4/件; 快递 \$18.99/件, 续 \$5/件; 满 \$89 免邮
--    东亚(zone=4,JP/KR)：标准 \$6.99/件, 续 \$3/件; 快递 \$13.99/件, 续 \$4/件; 满 \$69 免邮
--    东南亚(zone=5,SG)：标准 \$5.99/件, 续 \$2.5/件; 快递 \$11.99/件, 续 \$3/件; 满 \$59 免邮
--    INSERT IGNORE 防止重跑冲突（id 是主键；不会破坏已存在的行）
INSERT IGNORE INTO `mo_shipping_rate`
    (`id`, `zone_id`, `method_id`, `charge_type`, `first_charge`, `first_unit`, `continue_charge`, `continue_unit`, `free_threshold`, `currency`, `priority`, `status`, `remark`) VALUES
    -- 北美 (zone_id=1) 标准配送
    (210000001, 1, 200000001, 1, 5.99,  1, 2.50, 1, 59.00, 'USD', 10, 'ACTIVE', '北美标准配送：满 $59 免邮'),
    -- 北美 快递
    (210000002, 1, 200000002, 1, 12.99, 1, 3.50, 1, NULL,  'USD', 20, 'ACTIVE', '北美快递配送'),
    -- 北美 优先
    (210000003, 1, 200000003, 1, 24.99, 1, 5.00, 1, NULL,  'USD', 30, 'ACTIVE', '北美优先配送'),
    -- 西欧 (zone_id=2)
    (210000010, 2, 200000001, 1, 7.99,  1, 3.00, 1, 69.00, 'USD', 10, 'ACTIVE', '西欧标准配送'),
    (210000011, 2, 200000002, 1, 15.99, 1, 4.00, 1, NULL,  'USD', 20, 'ACTIVE', '西欧快递配送'),
    -- 大洋洲 (zone_id=3)
    (210000020, 3, 200000001, 1, 9.99,  1, 4.00, 1, 89.00, 'USD', 10, 'ACTIVE', '大洋洲标准配送'),
    (210000021, 3, 200000002, 1, 18.99, 1, 5.00, 1, NULL,  'USD', 20, 'ACTIVE', '大洋洲快递配送'),
    -- 东亚 (zone_id=4)
    (210000030, 4, 200000001, 1, 6.99,  1, 3.00, 1, 69.00, 'USD', 10, 'ACTIVE', '东亚标准配送'),
    (210000031, 4, 200000002, 1, 13.99, 1, 4.00, 1, NULL,  'USD', 20, 'ACTIVE', '东亚快递配送'),
    -- 东南亚 (zone_id=5)
    (210000040, 5, 200000001, 1, 5.99,  1, 2.50, 1, 59.00, 'USD', 10, 'ACTIVE', '东南亚标准配送'),
    (210000041, 5, 200000002, 1, 11.99, 1, 3.00, 1, NULL,  'USD', 20, 'ACTIVE', '东南亚快递配送');

-- 6) 给现有 5 个 zone 的所有 ACTIVE 发货策略绑定 standard + express（保持原 ShippingStrategy.method 字段兼容）
--    使用 INSERT IGNORE 防止重跑冲突（strategy_id + method_id 复合主键）
INSERT IGNORE INTO `mo_shipping_strategy_method` (`strategy_id`, `method_id`)
SELECT s.id, 200000001 FROM `mo_shipping_strategy` s
WHERE s.status = 'ACTIVE';

INSERT IGNORE INTO `mo_shipping_strategy_method` (`strategy_id`, `method_id`)
SELECT s.id, 200000002 FROM `mo_shipping_strategy` s
WHERE s.status = 'ACTIVE';
