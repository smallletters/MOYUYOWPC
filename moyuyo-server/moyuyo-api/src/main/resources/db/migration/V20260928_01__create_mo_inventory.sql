-- ============================================================
-- V20260928_01__create_mo_inventory.sql
-- 新增仓库库存关系表 mo_inventory：建立 SKU ↔ 仓库的库存映射。
-- 背景：原模型 mo_product.stock / mo_product_sku.stock 仅记录"全平台汇总库存"，
--       仓库管理页需要按仓库维度的库存，本次新增关系表支撑该场景。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_inventory` (
    `id`              BIGINT       NOT NULL                COMMENT '雪花ID',
    `warehouse_id`    BIGINT       NOT NULL                COMMENT '仓库ID（→mo_warehouse.id）',
    `product_id`      BIGINT       NOT NULL                COMMENT '商品ID（→mo_product.id）',
    `sku_id`          BIGINT       NULL                    COMMENT 'SKU ID（→mo_product_sku.id，可空：仅记录SPU级）',
    `quantity`        INT          NOT NULL DEFAULT 0      COMMENT '当前在库数量',
    `locked_quantity` INT          NOT NULL DEFAULT 0      COMMENT '锁定中数量（已下单未发货）',
    `safety_stock`    INT          NOT NULL DEFAULT 0      COMMENT '安全库存下限',
    `last_in_time`    DATETIME     NULL                    COMMENT '最近一次入库时间',
    `remark`           VARCHAR(255) NULL                    COMMENT '备注',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_wh_sku` (`warehouse_id`, `sku_id`),
    KEY `idx_inventory_wh_product` (`warehouse_id`, `product_id`),
    KEY `idx_inventory_product`    (`product_id`),
    KEY `idx_inventory_warehouse`  (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库库存关系表';

-- ============================================================
-- 初始化种子数据：
-- 把现有 10 个商品（183000001~183000010）按"主仓优先 + 海外仓分布"分配初始库存，
-- 覆盖前 5 个自营仓 + 前 3 个海外仓，确保前端首屏有真实数据可看。
-- 数量取自 mo_product.stock * 分配比例（四舍五入）。
-- ============================================================

-- 上海自营仓 (id=194000001, OVERSEAS=0)：分配 ~40% 商品库存
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000001, 194000001, 183000001, 184000001, 200,  50,  NOW(), '上海自营仓 - 宠物洗发水'),
    (196000002, 194000001, 183000002, 184000002, 32,   10,  NOW(), '上海自营仓 - 冬季外套 M'),
    (196000003, 194000001, 183000002, 184000003, 24,   10,  NOW(), '上海自营仓 - 冬季外套 L'),
    (196000004, 194000001, 183000003, 184000005, 32,   8,   NOW(), '上海自营仓 - 矫形床'),
    (196000005, 194000001, 183000004, 184000006, 400,  100, NOW(), '上海自营仓 - 益智球'),
    (196000006, 194000001, 183000005, 184000007, 60,   15,  NOW(), '上海自营仓 - 自动喂食器');

-- 广州自营仓 (id=194000002)：分配 ~25% 商品库存
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000007, 194000002, 183000006, 184000008, 60,   15,  NOW(), '广州自营仓 - 牵引套装 M'),
    (196000008, 194000002, 183000006, 184000009, 80,   20,  NOW(), '广州自营仓 - 牵引套装 L'),
    (196000009, 194000002, 183000007, 184000010, 320,  80,  NOW(), '广州自营仓 - 洁齿骨'),
    (196000010, 194000002, 183000008, 184000011, 240,  60,  NOW(), '广州自营仓 - 便携水壶');

-- 成都自营仓 (id=194000003)：分配 ~15% 商品库存
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000011, 194000003, 183000009, 184000012, 180,  45,  NOW(), '成都自营仓 - 耳朵清洁湿巾'),
    (196000012, 194000003, 183000010, 184000013, 100,  25,  NOW(), '成都自营仓 - 高架食盆'),
    (196000013, 194000003, 183000001, 184000001, 100,  20,  NOW(), '成都自营仓 - 宠物洗发水'),
    (196000014, 194000003, 183000007, 184000010, 240,  60,  NOW(), '成都自营仓 - 洁齿骨');

-- 美国洛杉矶海外仓 (id=194000006, OVERSEAS=1)：分配部分商品
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000015, 194000006, 183000002, 184000002, 48,   12,  NOW(), '洛杉矶仓 - 冬季外套 M'),
    (196000016, 194000006, 183000002, 184000003, 36,   10,  NOW(), '洛杉矶仓 - 冬季外套 L'),
    (196000017, 194000006, 183000006, 184000008, 90,   20,  NOW(), '洛杉矶仓 - 牵引套装 M'),
    (196000018, 194000006, 183000006, 184000009, 120,  30,  NOW(), '洛杉矶仓 - 牵引套装 L'),
    (196000019, 194000006, 183000003, 184000005, 48,   12,  NOW(), '洛杉矶仓 - 矫形床');

-- 德国汉堡海外仓 (id=194000007)：分配部分商品
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000020, 194000007, 183000004, 184000006, 600,  150, NOW(), '汉堡仓 - 益智球'),
    (196000021, 194000007, 183000005, 184000007, 90,   20,  NOW(), '汉堡仓 - 自动喂食器'),
    (196000022, 194000007, 183000008, 184000011, 360,  90,  NOW(), '汉堡仓 - 便携水壶'),
    (196000023, 194000007, 183000010, 184000013, 150,  40,  NOW(), '汉堡仓 - 高架食盆');

-- 日本东京海外仓 (id=194000008)：分配部分商品
INSERT IGNORE INTO `mo_inventory`
    (`id`, `warehouse_id`, `product_id`, `sku_id`, `quantity`, `safety_stock`, `last_in_time`, `remark`)
VALUES
    (196000024, 194000008, 183000001, 184000001, 200,  50,  NOW(), '东京仓 - 宠物洗发水'),
    (196000025, 194000008, 183000007, 184000010, 240,  60,  NOW(), '东京仓 - 洁齿骨'),
    (196000026, 194000008, 183000009, 184000012, 270,  70,  NOW(), '东京仓 - 耳朵清洁湿巾');