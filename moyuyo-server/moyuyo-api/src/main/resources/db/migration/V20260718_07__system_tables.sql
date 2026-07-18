-- ============================================================
-- V20260718_07__system_tables.sql
-- 系统配置 / 推送记录 / 投诉处理 / 库存盘点 / GDPR政策
-- ============================================================

-- 1. 系统配置表
CREATE TABLE IF NOT EXISTS `mo_system_config` (
    `id` BIGINT PRIMARY KEY,
    `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `remark` VARCHAR(255) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 2. 推送记录表
CREATE TABLE IF NOT EXISTS `mo_push_record` (
    `id` BIGINT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL COMMENT '推送标题',
    `content` TEXT COMMENT '推送内容',
    `type` VARCHAR(32) NOT NULL COMMENT '推送类型：NOTICE/PROMOTION/SYSTEM',
    `target_type` VARCHAR(32) DEFAULT 'ALL' COMMENT '推送目标：ALL/USER/TAG',
    `target_ids` TEXT COMMENT '目标用户ID列表(JSON)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SCHEDULED/SENDING/SENT/FAILED',
    `scheduled_time` DATETIME COMMENT '定时发送时间',
    `sent_time` DATETIME COMMENT '实际发送时间',
    `success_count` INT DEFAULT 0 COMMENT '成功发送数',
    `fail_count` INT DEFAULT 0 COMMENT '发送失败数',
    `create_by` BIGINT COMMENT '创建人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_scheduled_time` (`scheduled_time` DESC),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送记录表';

-- 3. 投诉处理记录表
CREATE TABLE IF NOT EXISTS `mo_complaint_process` (
    `id` BIGINT PRIMARY KEY,
    `complaint_id` BIGINT NOT NULL COMMENT '投诉ID',
    `action` VARCHAR(64) NOT NULL COMMENT '处理动作',
    `operator` VARCHAR(64) COMMENT '处理人',
    `remark` VARCHAR(500) COMMENT '处理备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_complaint_id` (`complaint_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉处理记录表';

-- 4. 库存盘点表
CREATE TABLE IF NOT EXISTS `mo_inventory_check` (
    `id` BIGINT PRIMARY KEY,
    `check_no` VARCHAR(64) NOT NULL COMMENT '盘点单号',
    `sku_id` BIGINT COMMENT 'SKU ID',
    `product_name` VARCHAR(200) COMMENT '商品名称',
    `sku_spec` VARCHAR(100) COMMENT 'SKU规格',
    `book_quantity` INT DEFAULT 0 COMMENT '账面库存',
    `actual_quantity` INT COMMENT '实际库存',
    `difference` INT DEFAULT 0 COMMENT '差异数量',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED',
    `checker` VARCHAR(64) COMMENT '盘点人',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `complete_time` DATETIME COMMENT '完成时间',
    INDEX `idx_status` (`status`),
    INDEX `idx_check_no` (`check_no`),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点表';

-- 5. GDPR 政策版本表
CREATE TABLE IF NOT EXISTS `mo_gdpr_policy` (
    `id` BIGINT PRIMARY KEY,
    `version` VARCHAR(32) NOT NULL COMMENT '版本号',
    `title` VARCHAR(200) COMMENT '政策标题',
    `content` TEXT COMMENT '政策内容',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DRAFT/DEPRECATED',
    `effective_date` DATE COMMENT '生效日期',
    `create_by` BIGINT COMMENT '创建人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_version` (`version`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR政策版本表';
