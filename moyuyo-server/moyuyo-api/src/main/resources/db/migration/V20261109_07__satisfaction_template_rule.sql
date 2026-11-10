-- ============================================================
-- V20261109_07__satisfaction_template_rule.sql
-- 满意度管理：问卷模板 + 调研触发规则
--
-- 说明：
--   1. mo_satisfaction_template：问卷模板（管理后台「满意度管理 → 问卷模板」）
--   2. mo_satisfaction_rule：调研触发规则（订单完成 / 客服关闭 / 退款完成）
--   3. 触发延迟与发送渠道复用 mo_system_config 存储
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_satisfaction_template` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '模板名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '模板说明',
    `questions`   INT          NOT NULL DEFAULT 0 COMMENT '题目数量',
    `minutes`     DECIMAL(4,1) NOT NULL DEFAULT 1.0 COMMENT '预计填写时长（分钟）',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT '状态：active/disabled/draft',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '满意度问卷模板';

CREATE TABLE IF NOT EXISTS `mo_satisfaction_rule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_key`    VARCHAR(60)  NOT NULL COMMENT '规则编码',
    `label`       VARCHAR(100) NOT NULL COMMENT '规则名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
    `sort_no`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_satisfaction_rule_key` (`rule_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '满意度调研触发规则';

INSERT IGNORE INTO `mo_satisfaction_template` (`id`, `name`, `description`, `questions`, `minutes`, `status`, `create_time`, `update_time`)
VALUES
(9001, '订单满意度调研', '订单完成后评估购物体验', 8, 2.0, 'active', NOW(), NOW()),
(9002, '客服满意度调研', '客服会话结束后评估服务', 5, 1.0, 'active', NOW(), NOW()),
(9003, '退款体验调研', '退款完成后了解用户反馈', 6, 1.5, 'disabled', NOW(), NOW()),
(9004, '物流体验调研', '评估配送时效与包裹完好性', 4, 1.0, 'draft', NOW(), NOW());

INSERT IGNORE INTO `mo_satisfaction_rule` (`id`, `rule_key`, `label`, `description`, `enabled`, `sort_no`, `create_time`, `update_time`)
VALUES
(9101, 'ORDER_COMPLETED', '订单完成后', '用户确认收货后触发调研', 1, 1, NOW(), NOW()),
(9102, 'CS_CLOSED', '客服关闭后', '客服工单完结后触发调研', 1, 2, NOW(), NOW()),
(9103, 'REFUND_COMPLETED', '退款完成后', '退款到账后触发调研', 0, 3, NOW(), NOW());

-- 触发延迟（小时）与发送渠道（JSON 数组）
INSERT IGNORE INTO mo_system_config (id, config_key, config_value, remark, create_time, update_time)
VALUES
(208001001, 'satisfaction.delay_hours', '24', '满意度调研触发延迟（小时）', NOW(), NOW()),
(208001002, 'satisfaction.channels', '["App 内","邮件"]', '满意度调研发送渠道（JSON 数组）', NOW(), NOW());
