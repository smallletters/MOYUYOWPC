-- ============================================================
-- V20260720_01__admin_missing_modules.sql
-- 补齐管理后台缺失功能的数据表：
-- 商品审核 / 内容审核 / 风控告警 / 订单标签 / 客服会话 / 库存调拨
-- ============================================================

-- 1. 商品审核记录表
CREATE TABLE IF NOT EXISTS `mo_product_approval` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `submitter_id` BIGINT COMMENT '提交人（管理员ID）',
    `type` VARCHAR(16) NOT NULL COMMENT '审核类型：NEW/UPDATE/ONSHELF/OFFSHELF/DELIST',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/URGENT',
    `reason` VARCHAR(500) COMMENT '驳回原因',
    `reviewer_id` BIGINT COMMENT '审核人（管理员ID）',
    `review_time` DATETIME COMMENT '审核时间',
    `urgent_flag` TINYINT(1) DEFAULT 0 COMMENT '加急标识',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_product` (`product_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品审核记录表';

-- 2. 内容审核记录表（社区帖子/评论/用户头像等）
CREATE TABLE IF NOT EXISTS `mo_content_review` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `content_type` VARCHAR(16) NOT NULL COMMENT 'POST/COMMENT/AVATAR/NICKNAME/REVIEW',
    `content_id` BIGINT NOT NULL COMMENT '对应内容ID',
    `user_id` BIGINT COMMENT '发布者ID',
    `content_excerpt` VARCHAR(500) COMMENT '内容摘要',
    `images` JSON COMMENT '违规图片',
    `reason` VARCHAR(16) COMMENT '违规原因：SPAM/PORNOGRAPHY/POLITICS/HATE/VIOLENCE/OTHER',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/DELETED',
    `reviewer_id` BIGINT COMMENT '审核人',
    `review_comment` VARCHAR(500) COMMENT '审核备注',
    `review_time` DATETIME COMMENT '审核时间',
    `auto_flag` TINYINT(1) DEFAULT 0 COMMENT '是否自动审核',
    `auto_score` DECIMAL(5,2) COMMENT '自动审核置信度',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_content` (`content_type`, `content_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容审核记录表';

-- 3. 风控告警配置表
CREATE TABLE IF NOT EXISTS `mo_risk_alert_config` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `rule_id` BIGINT COMMENT '关联风控规则ID',
    `alert_type` VARCHAR(16) NOT NULL COMMENT 'THRESHOLD/FREQUENCY/TREND',
    `alert_name` VARCHAR(128) NOT NULL COMMENT '告警名称',
    `metric` VARCHAR(64) NOT NULL COMMENT '监控指标',
    `condition` VARCHAR(16) NOT NULL COMMENT 'GREATER_THAN/LESS_THAN/EQUAL',
    `threshold` DECIMAL(12,2) NOT NULL COMMENT '阈值',
    `notify_channels` JSON COMMENT '通知渠道：["EMAIL","SMS","DINGTALK"]',
    `notify_roles` JSON COMMENT '通知角色',
    `cooldown_minutes` INT DEFAULT 60 COMMENT '冷却时间(分钟)',
    `enabled` TINYINT(1) DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rule` (`rule_id`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控告警配置表';

-- 4. 订单标签表
CREATE TABLE IF NOT EXISTS `mo_order_tag` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `name` VARCHAR(32) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(16) COMMENT '标签颜色',
    `icon` VARCHAR(64) COMMENT '标签图标',
    `type` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    `auto_rule` JSON COMMENT '自动打标规则',
    `sort_order` INT DEFAULT 0,
    `enabled` TINYINT(1) DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单标签表';

-- 5. 订单-标签关联表
CREATE TABLE IF NOT EXISTS `mo_order_tag_rel` (
    `order_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`order_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单标签关联表';

-- 6. 客服会话记录表
CREATE TABLE IF NOT EXISTS `mo_cs_session` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `operator_id` BIGINT COMMENT '客服管理员ID',
    `channel` VARCHAR(16) NOT NULL COMMENT 'APP/WEB/EMAIL/PHONE',
    `category` VARCHAR(32) COMMENT '问题分类',
    `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/WAITING/CLOSED/TRANSFERRED',
    `satisfaction` TINYINT COMMENT '满意度评分1-5',
    `first_response_time` INT COMMENT '首次响应时长(秒)',
    `resolve_time` INT COMMENT '解决时长(秒)',
    `message_count` INT DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `close_time` DATETIME,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_operator` (`operator_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话记录表';

-- 7. 库存调拨记录表
CREATE TABLE IF NOT EXISTS `mo_inventory_transfer` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `from_warehouse_id` BIGINT COMMENT '调出仓库',
    `to_warehouse_id` BIGINT COMMENT '调入仓库',
    `quantity` INT NOT NULL COMMENT '调拨数量',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_TRANSIT/COMPLETED/REJECTED',
    `operator_id` BIGINT COMMENT '操作人',
    `approver_id` BIGINT COMMENT '审批人',
    `reason` VARCHAR(500) COMMENT '调拨原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `complete_time` DATETIME,
    INDEX `idx_sku` (`sku_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_from_warehouse` (`from_warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存调拨记录表';

-- 8. 商品问答表
CREATE TABLE IF NOT EXISTS `mo_product_qa` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `user_id` BIGINT NOT NULL COMMENT '提问用户ID',
    `question` VARCHAR(500) NOT NULL COMMENT '问题内容',
    `answer` VARCHAR(1000) COMMENT '回答内容',
    `answerer_id` BIGINT COMMENT '回答者（管理员ID）',
    `answer_time` DATETIME COMMENT '回答时间',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT(1) DEFAULT 1 COMMENT '0隐藏 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_product` (`product_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品问答表';

-- 9. 众筹活动表
CREATE TABLE IF NOT EXISTS `mo_crowdfunding` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `product_id` BIGINT NOT NULL COMMENT '关联商品ID',
    `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `goal_amount` DECIMAL(12,2) NOT NULL COMMENT '目标金额',
    `current_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '当前已筹金额',
    `min_pledge` DECIMAL(10,2) COMMENT '最低支持金额',
    `max_backers` INT DEFAULT 0 COMMENT '限制支持人数(0不限)',
    `current_backers` INT DEFAULT 0 COMMENT '当前支持人数',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/SUCCESSFUL/FAILED/CANCELLED',
    `description` TEXT COMMENT '活动描述',
    `rewards_json` JSON COMMENT '回报档位配置',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_product` (`product_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='众筹活动表';

-- 10. 众筹支持记录表
CREATE TABLE IF NOT EXISTS `mo_crowdfunding_pledge` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `crowdfunding_id` BIGINT NOT NULL COMMENT '众筹活动ID',
    `user_id` BIGINT NOT NULL COMMENT '支持用户ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支持金额',
    `reward_tier` VARCHAR(64) COMMENT '回报档位',
    `message` VARCHAR(500) COMMENT '支持留言',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PAID' COMMENT 'PAID/REFUNDING/REFUNDED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_crowdfunding` (`crowdfunding_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='众筹支持记录表';
