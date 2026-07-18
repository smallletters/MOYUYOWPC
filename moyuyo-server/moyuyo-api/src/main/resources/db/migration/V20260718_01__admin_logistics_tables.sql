-- ============================================================
-- V20260718_01__admin_logistics_tables.sql
-- 管理后台缺失表：导入导出 / 仓库 / 承运商 / 清关 / 客服绩效
-- ============================================================

-- 1. 数据导出/导入请求记录
CREATE TABLE IF NOT EXISTS `mo_data_export_request` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT COMMENT '操作人ID',
    `export_id` VARCHAR(64) COMMENT '导出标识',
    `request_type` VARCHAR(32) NOT NULL COMMENT '导入/导出类型：商品导入/订单导出/用户导入',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
    `download_url` VARCHAR(500) COMMENT '下载链接',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `complete_time` DATETIME,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据导出/导入请求记录';

-- 2. 仓库表
CREATE TABLE IF NOT EXISTS `mo_warehouse` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '仓库名称',
    `type` VARCHAR(16) NOT NULL COMMENT 'SELF(自营)/THIRD_PARTY(第三方)/OVERSEAS(海外)',
    `country` VARCHAR(64) COMMENT '国家',
    `city` VARCHAR(64) COMMENT '城市',
    `address` VARCHAR(255) COMMENT '详细地址',
    `area` INT DEFAULT 0 COMMENT '面积(㎡)',
    `manager` VARCHAR(64) COMMENT '负责人',
    `phone` VARCHAR(32) COMMENT '联系电话',
    `sku_count` INT DEFAULT 0 COMMENT 'SKU数量',
    `total_stock` INT DEFAULT 0 COMMENT '总库存',
    `usage_rate` INT DEFAULT 0 COMMENT '使用率(%)',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/MAINTENANCE',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_type` (`type`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

-- 3. 承运商表
CREATE TABLE IF NOT EXISTS `mo_carrier` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '承运商名称',
    `transport_mode` VARCHAR(16) COMMENT 'AIR(航空)/LAND(陆运)/SEA(海运)/MIX(混合)',
    `avg_delivery_days` DECIMAL(4,1) DEFAULT 0 COMMENT '平均配送天数',
    `first_weight_price` DECIMAL(10,2) DEFAULT 0 COMMENT '首重价格',
    `renew_weight_price` DECIMAL(10,2) DEFAULT 0 COMMENT '续重价格',
    `praise_rate` DECIMAL(5,1) DEFAULT 0 COMMENT '好评率(%)',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='承运商表';

-- 4. 清关记录表
CREATE TABLE IF NOT EXISTS `mo_clearance` (
    `id` BIGINT PRIMARY KEY,
    `declaration_no` VARCHAR(64) NOT NULL COMMENT '报关单号',
    `order_no` VARCHAR(64) COMMENT '关联订单号',
    `product_name` VARCHAR(200) COMMENT '商品名称',
    `hs_code` VARCHAR(32) COMMENT 'HS编码',
    `tax_rate` DECIMAL(5,1) DEFAULT 0 COMMENT '税率(%)',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/INSPECTING/CLEARED/REJECTED',
    `declare_time` DATETIME COMMENT '申报时间',
    `clearance_time` DATETIME COMMENT '清关完成时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`),
    INDEX `idx_declaration_no` (`declaration_no`),
    INDEX `idx_declare_time` (`declare_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清关记录表';

-- 5. 发货策略表
CREATE TABLE IF NOT EXISTS `mo_shipping_strategy` (
    `id` BIGINT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL COMMENT '策略名称',
    `region` VARCHAR(64) COMMENT '适用区域',
    `method` VARCHAR(64) COMMENT '配送方式',
    `rule_desc` VARCHAR(255) COMMENT '计费规则描述',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货策略表';

-- 6. 合包记录表
CREATE TABLE IF NOT EXISTS `mo_merge_package` (
    `id` BIGINT PRIMARY KEY,
    `merge_no` VARCHAR(64) NOT NULL COMMENT '合包单号',
    `order_count` INT DEFAULT 0 COMMENT '订单数量',
    `package_count` INT DEFAULT 0 COMMENT '包裹数量',
    `total_weight` DECIMAL(10,2) DEFAULT 0 COMMENT '总重量(kg)',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `complete_time` DATETIME,
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合包记录表';

-- 7. 分包裹记录表
CREATE TABLE IF NOT EXISTS `mo_split_package` (
    `id` BIGINT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `product_count` INT DEFAULT 0 COMMENT '商品数量',
    `split_count` INT DEFAULT 0 COMMENT '分包裹数量',
    `total_weight` DECIMAL(10,2) DEFAULT 0 COMMENT '总重量(kg)',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `complete_time` DATETIME,
    INDEX `idx_order_no` (`order_no`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分包裹记录表';

-- 8. 客服绩效表
CREATE TABLE IF NOT EXISTS `mo_cs_performance` (
    `id` BIGINT PRIMARY KEY,
    `agent_name` VARCHAR(64) NOT NULL COMMENT '客服姓名',
    `department` VARCHAR(64) COMMENT '部门',
    `total_tickets` INT DEFAULT 0 COMMENT '累计工单数',
    `today_tickets` INT DEFAULT 0 COMMENT '今日处理工单',
    `avg_response_time` DECIMAL(4,1) DEFAULT 0 COMMENT '平均响应时间(小时)',
    `satisfaction_score` DECIMAL(3,1) DEFAULT 0 COMMENT '满意度评分(1-5)',
    `today_online_duration` VARCHAR(16) COMMENT '今日在线时长',
    `status` VARCHAR(16) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE/BUSY',
    `latest_login_time` DATETIME COMMENT '最后登录时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服绩效表';
