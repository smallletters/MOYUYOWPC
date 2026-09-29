-- ============================================================
-- 发货区域（Shipping Zone）落地
-- 目的：把 APP 可发货地址白名单从硬编码常量迁到可后台配置的表
-- 关联：mo_shipping_strategy.zone_id 指向本表，策略绑定到具体区域
-- ============================================================

-- 1) 新建区域表
CREATE TABLE IF NOT EXISTS `mo_shipping_zone` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '区域名称，如 北美、欧盟、东南亚',
    `country_codes` VARCHAR(512) NOT NULL COMMENT '国家码列表，逗号分隔，ISO 3166-1 alpha-2 大写，如 US,CA',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `sort_order` INT DEFAULT 0 COMMENT '展示排序，升序',
    `remark` VARCHAR(255) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货区域表';

-- 2) 写入初始 12 国数据（与 AddressServiceImpl 旧硬编码保持一致）
INSERT INTO `mo_shipping_zone` (`id`, `name`, `country_codes`, `status`, `sort_order`, `remark`)
VALUES
    (1, '北美',     'US,CA',              'ACTIVE', 10, '美国、加拿大'),
    (2, '西欧核心', 'GB,DE,FR,IT,ES',     'ACTIVE', 20, '英德法意西'),
    (3, '大洋洲',   'AU,NZ',              'ACTIVE', 30, '澳大利亚、新西兰'),
    (4, '东亚',     'JP,KR',              'ACTIVE', 40, '日本、韩国'),
    (5, '东南亚',   'SG',                 'ACTIVE', 50, '新加坡');

-- 3) 给发货策略表加 zone_id 外键（默认 1=北美，确保存量数据不破）
ALTER TABLE `mo_shipping_strategy`
    ADD COLUMN `zone_id` BIGINT DEFAULT 1 COMMENT '所属发货区域 id，关联 mo_shipping_zone.id' AFTER `region`,
    ADD INDEX `idx_zone_id` (`zone_id`);