-- ============================================================
-- V20260819_03__inventory_batch_and_sku_update_time.sql
-- 1) 新建 mo_inventory_batch（批次表），驱动"库存管理 > 批次管理"真实数据
--    字段：批次号 / SKU / 商品名 / 入库日期 / 有效期 / 数量 / 出库策略 / 状态
--    状态机：NORMAL(在库)/ EXPIRING(临期 <=30天)/ EXPIRED(已过期)
--    出库策略：FIFO / FEFO（按有效期生效）
-- 2) 给 mo_product_sku 加 update_time（IF NOT EXISTS 幂等列），
--    驱动"商品库存"表格的"最后更新时间"列显示真实值
-- 3) 同步给 ProductSkuEntity 实体新增 update_time 字段（由 MyBatis-Plus 自动填充）
-- ============================================================

-- 1. 新建库存批次表（幂等）
CREATE TABLE IF NOT EXISTS `mo_inventory_batch` (
    `id`            BIGINT       NOT NULL,
    `batch_no`      VARCHAR(64)  NOT NULL COMMENT '批次编号',
    `sku_id`        BIGINT       NOT NULL COMMENT 'SKU ID',
    `product_name`  VARCHAR(200) NOT NULL COMMENT '商品名称（冗余存储便于列表展示）',
    `in_date`       DATETIME     NOT NULL COMMENT '入库日期',
    `expire_date`   DATETIME     NULL COMMENT '有效期至（非食品可为空）',
    `quantity`      INT          NOT NULL DEFAULT 0 COMMENT '批次库存数量',
    `strategy`      VARCHAR(16)  NOT NULL DEFAULT 'FIFO' COMMENT '出库策略：FIFO / FEFO',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / EXPIRING / EXPIRED',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_sku` (`sku_id`),
    KEY `idx_expire_date` (`expire_date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存批次表';

-- 2. mo_product_sku 增加 update_time（幂等列），MyBatis-Plus 自动填充
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 守卫保持幂等
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_product_sku' AND COLUMN_NAME='update_time');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `mo_product_sku` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `sales`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. seed 数据：每个已知商品至少一条批次，混合 NORMAL/EXPIRING/EXPIRED，
--    这样后台"批次管理"页面立刻能展示真实数据，避免空表。
--    严格幂等：仅在 mo_inventory_batch 还没有任何记录时插入一次。
INSERT INTO `mo_inventory_batch`
    (`id`, `batch_no`, `sku_id`, `product_name`, `in_date`, `expire_date`, `quantity`, `strategy`, `status`)
SELECT 10001, 'B20260615-001', 1, '皇家猫粮室内成猫 2kg',
       DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_ADD(CURDATE(), INTERVAL 12 DAY),  5,  'FEFO', 'EXPIRING'
WHERE NOT EXISTS (SELECT 1 FROM `mo_inventory_batch` WHERE `batch_no` = 'B20260615-001');

INSERT INTO `mo_inventory_batch`
    (`id`, `batch_no`, `sku_id`, `product_name`, `in_date`, `expire_date`, `quantity`, `strategy`, `status`)
SELECT 10002, 'B20260701-012', 2, '渴望六种鱼猫粮 5.4kg',
       DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_ADD(CURDATE(), INTERVAL 340 DAY), 12, 'FIFO',  'NORMAL'
WHERE NOT EXISTS (SELECT 1 FROM `mo_inventory_batch` WHERE `batch_no` = 'B20260701-012');

INSERT INTO `mo_inventory_batch`
    (`id`, `batch_no`, `sku_id`, `product_name`, `in_date`, `expire_date`, `quantity`, `strategy`, `status`)
SELECT 10003, 'B20260520-003', 3, '宠物益生菌调理粉 120g',
       DATE_SUB(CURDATE(), INTERVAL 70 DAY), DATE_ADD(CURDATE(), INTERVAL 24 DAY),  18, 'FEFO', 'EXPIRING'
WHERE NOT EXISTS (SELECT 1 FROM `mo_inventory_batch` WHERE `batch_no` = 'B20260520-003');

INSERT INTO `mo_inventory_batch`
    (`id`, `batch_no`, `sku_id`, `product_name`, `in_date`, `expire_date`, `quantity`, `strategy`, `status`)
SELECT 10004, 'B20260705-018', 4, '豆腐猫砂 6L 经典款',
       DATE_SUB(CURDATE(), INTERVAL 25 DAY), DATE_ADD(CURDATE(), INTERVAL 300 DAY), 40, 'FEFO', 'NORMAL'
WHERE NOT EXISTS (SELECT 1 FROM `mo_inventory_batch` WHERE `batch_no` = 'B20260705-018');

INSERT INTO `mo_inventory_batch`
    (`id`, `batch_no`, `sku_id`, `product_name`, `in_date`, `expire_date`, `quantity`, `strategy`, `status`)
SELECT 10005, 'B20260410-005', 5, '全价冻干双拼粮 1.8kg',
       DATE_SUB(CURDATE(), INTERVAL 110 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY),  0, 'FIFO', 'EXPIRED'
WHERE NOT EXISTS (SELECT 1 FROM `mo_inventory_batch` WHERE `batch_no` = 'B20260410-005');
