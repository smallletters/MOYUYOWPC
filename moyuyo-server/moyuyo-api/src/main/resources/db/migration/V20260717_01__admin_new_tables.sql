-- ============================================================
-- MOYUYO V20260717_01 - 管理后台新增模块数据库表
-- 包含：应用版本/审计日志/知识库/满意度/风控/GDPR/价格策略/短信/运营
-- ============================================================

-- 1. 应用版本管理
CREATE TABLE IF NOT EXISTS `mo_app_version` (
    `id` BIGINT PRIMARY KEY,
    `app_type` VARCHAR(20) NOT NULL COMMENT 'IOS/ANDROID',
    `version_code` INT NOT NULL COMMENT '版本号（数字）',
    `version_name` VARCHAR(32) NOT NULL COMMENT '版本号（显示）',
    `update_title` VARCHAR(200) COMMENT '更新标题',
    `update_desc` TEXT COMMENT '更新说明',
    `download_url` VARCHAR(500) COMMENT '下载链接',
    `force_update` BIT DEFAULT 0 COMMENT '是否强制更新',
    `file_size` VARCHAR(32) COMMENT '文件大小',
    `status` VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ROLLED_BACK',
    `publish_time` DATETIME COMMENT '发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_app_type_version` (`app_type`, `version_code` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用版本管理';

-- 2. 操作审计日志
CREATE TABLE IF NOT EXISTS `mo_audit_log` (
    `id` BIGINT PRIMARY KEY,
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(64) COMMENT '操作人名称',
    `action` VARCHAR(64) NOT NULL COMMENT '操作动作 LOGIN/CREATE/UPDATE/DELETE/EXPORT',
    `module` VARCHAR(64) NOT NULL COMMENT '操作模块 ORDER/PRODUCT/USER/SYSTEM',
    `resource_id` VARCHAR(64) COMMENT '资源ID',
    `detail` VARCHAR(500) COMMENT '操作详情',
    `ip` VARCHAR(64) COMMENT '操作IP',
    `user_agent` VARCHAR(500) COMMENT 'UA信息',
    `result` VARCHAR(8) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILURE',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_operator_time` (`operator_id`, `create_time` DESC),
    INDEX `idx_action_time` (`action`, `create_time` DESC),
    INDEX `idx_module_time` (`module`, `create_time` DESC),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- 3. 知识库
CREATE TABLE IF NOT EXISTS `mo_knowledge_base` (
    `id` BIGINT PRIMARY KEY,
    `category` VARCHAR(32) NOT NULL COMMENT 'FAQ/GUIDE/POLICY/TRAINING',
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `tags` VARCHAR(500) COMMENT '标签（逗号分隔）',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `status` VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    `create_by` BIGINT COMMENT '创建人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`),
    FULLTEXT INDEX `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

-- 4. 满意度调查
CREATE TABLE IF NOT EXISTS `mo_satisfaction_survey` (
    `id` BIGINT PRIMARY KEY,
    `ticket_id` BIGINT COMMENT '关联工单ID',
    `order_id` BIGINT COMMENT '关联订单ID',
    `user_id` BIGINT NOT NULL,
    `score` TINYINT NOT NULL COMMENT '评分 1-5',
    `category` VARCHAR(32) COMMENT 'SERVICE/QUALITY/LOGISTICS',
    `comment` TEXT COMMENT '评价内容',
    `dimensions_json` JSON COMMENT '各维度评分',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ticket_id` (`ticket_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满意度调查';

-- 5. 风控规则
CREATE TABLE IF NOT EXISTS `mo_risk_rule` (
    `id` BIGINT PRIMARY KEY,
    `rule_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '规则编码',
    `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `rule_type` VARCHAR(32) NOT NULL COMMENT 'LOGIN/ORDER/PAYMENT/COUPON',
    `condition_json` JSON NOT NULL COMMENT '触发条件',
    `action` VARCHAR(32) NOT NULL COMMENT 'BLOCK/REVIEW/VERIFY/LOG',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `enabled` BIT DEFAULT 1,
    `description` VARCHAR(500),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rule_type` (`rule_type`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则';

-- 6. 风控事件
CREATE TABLE IF NOT EXISTS `mo_risk_event` (
    `id` BIGINT PRIMARY KEY,
    `rule_id` BIGINT COMMENT '命中规则ID',
    `rule_code` VARCHAR(64) COMMENT '命中规则编码',
    `user_id` BIGINT COMMENT '用户ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT 'LOGIN/ORDER/PAYMENT',
    `business_id` VARCHAR(64) COMMENT '业务ID（订单号等）',
    `risk_level` VARCHAR(16) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    `action_taken` VARCHAR(32) COMMENT 'BLOCK/REVIEW/VERIFY',
    `detail_json` JSON COMMENT '事件详情',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWED/RESOLVED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_rule_id` (`rule_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_event_type` (`event_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';

-- 7. GDPR 同意记录
CREATE TABLE IF NOT EXISTS `mo_gdpr_consent` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `consent_type` VARCHAR(32) NOT NULL COMMENT 'MARKETING/ANALYTICS/THIRD_PARTY/COOKIES',
    `granted` BIT NOT NULL DEFAULT 1 COMMENT '是否同意',
    `ip` VARCHAR(64) COMMENT '操作IP',
    `user_agent` VARCHAR(500) COMMENT 'UA信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_type` (`user_id`, `consent_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR同意记录';

-- 8. GDPR 数据请求
CREATE TABLE IF NOT EXISTS `mo_gdpr_request` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `request_type` VARCHAR(32) NOT NULL COMMENT 'ACCESS/DELETE/PORTABILITY/RECTIFY',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/REJECTED',
    `detail_json` JSON COMMENT '请求详情',
    `processed_by` BIGINT COMMENT '处理人ID',
    `processed_time` DATETIME COMMENT '处理时间',
    `response_note` VARCHAR(1000) COMMENT '处理备注',
    `expire_time` DATETIME COMMENT '处理截止时间（30天）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GDPR数据请求';

-- 9. 价格策略
CREATE TABLE IF NOT EXISTS `mo_price_strategy` (
    `id` BIGINT PRIMARY KEY,
    `strategy_name` VARCHAR(128) NOT NULL COMMENT '策略名称',
    `strategy_type` VARCHAR(32) NOT NULL COMMENT 'DISCOUNT/FLASH_SALE/MEMBER/GRADED',
    `apply_to` VARCHAR(16) NOT NULL COMMENT 'PRODUCT/CATEGORY/BRAND',
    `apply_value` BIGINT COMMENT '关联ID（商品ID/类目ID/品牌ID）',
    `discount_type` VARCHAR(16) NOT NULL COMMENT 'PERCENTAGE/FIXED_AMOUNT',
    `discount_value` DECIMAL(10,2) NOT NULL COMMENT '折扣值',
    `min_amount` DECIMAL(10,2) COMMENT '最低消费金额',
    `max_discount` DECIMAL(10,2) COMMENT '最高优惠金额',
    `start_time` DATETIME COMMENT '生效时间',
    `end_time` DATETIME COMMENT '失效时间',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `enabled` BIT DEFAULT 1,
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_strategy_type` (`strategy_type`),
    INDEX `idx_apply` (`apply_to`, `apply_value`),
    INDEX `idx_enabled` (`enabled`),
    INDEX `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格策略';

-- 10. 短信记录
CREATE TABLE IF NOT EXISTS `mo_sms_record` (
    `id` BIGINT PRIMARY KEY,
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `template_code` VARCHAR(64) NOT NULL COMMENT '模板编码',
    `content` VARCHAR(500) NOT NULL COMMENT '短信内容',
    `params_json` JSON COMMENT '模板参数',
    `channel` VARCHAR(32) DEFAULT 'ALIYUN' COMMENT '短信通道',
    `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `fail_reason` VARCHAR(500) COMMENT '失败原因',
    `send_time` DATETIME COMMENT '发送时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_phone` (`phone`),
    INDEX `idx_status` (`status`),
    INDEX `idx_send_time` (`send_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信记录';

-- 11. AdminOrderOps 使用已有的 mo_order / mo_order_item 表，无需新建
-- 12. AdminAnalysis 通过聚合查询已有表（mo_order, mo_product, mo_user）实现，无需新建
