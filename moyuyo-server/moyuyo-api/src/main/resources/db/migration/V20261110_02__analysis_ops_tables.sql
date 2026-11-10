-- ============================================================
-- V20261110_02__analysis_ops_tables.sql
-- 补齐三个此前硬编码/无后端的运营功能：
--   1. mo_search_keyword_action  搜索分析：选品需求 / 标记已处理
--   2. mo_warehouse_inbound_plan 仓库管理：入仓计划
--   3. mo_product_report         商品报表：商品举报
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_search_keyword_action` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `keyword`     VARCHAR(200) NOT NULL COMMENT '搜索词',
    `action_type` VARCHAR(20)  NOT NULL COMMENT 'SOURCING=选品需求 RESOLVED=标记已处理',
    `category`    VARCHAR(100) DEFAULT NULL COMMENT '目标品类',
    `target_user` VARCHAR(200) DEFAULT NULL COMMENT '目标人群',
    `priority`    VARCHAR(20)  DEFAULT NULL COMMENT '优先级',
    `operator`    VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_search_action_keyword` (`keyword`),
    KEY `idx_search_action_type` (`action_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '搜索词处理流水';

CREATE TABLE IF NOT EXISTS `mo_warehouse_inbound_plan` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_name`   VARCHAR(200) NOT NULL COMMENT '商品名称',
    `sku`            VARCHAR(100) DEFAULT NULL COMMENT 'SKU 编码',
    `warehouse_name` VARCHAR(100) NOT NULL COMMENT '目标仓库',
    `quantity`       INT          NOT NULL DEFAULT 0 COMMENT '计划入库数量',
    `expected_at`    DATE         DEFAULT NULL COMMENT '预计入库日期',
    `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/CANCELLED',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_inbound_warehouse` (`warehouse_name`),
    KEY `idx_inbound_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '仓库入仓计划';

CREATE TABLE IF NOT EXISTS `mo_product_report` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`     BIGINT       DEFAULT NULL COMMENT '商品ID',
    `product_name`   VARCHAR(200) NOT NULL COMMENT '商品名称',
    `report_type`    VARCHAR(30)  NOT NULL COMMENT 'FAKE_AD/QUALITY/INFRINGEMENT/BANNED',
    `reason`         VARCHAR(1000) NOT NULL COMMENT '举报原因',
    `reporter`       VARCHAR(100) DEFAULT NULL COMMENT '举报人',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE',
    `handle_result`  VARCHAR(100) DEFAULT NULL COMMENT '处理结果',
    `handle_comment` VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
    `sla_deadline`   DATETIME     DEFAULT NULL COMMENT '处理截止时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_report_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品举报';
