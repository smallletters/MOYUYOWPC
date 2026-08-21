-- ============================================================
-- V20260820_03__ticket_messages.sql
-- 工单对话消息表：客服与用户在工单内的回复历史
-- 与 mo_cs_session + mo_cs_message 是两套独立流转体系（工单 vs 在线客服）
-- 工单消息持久化时间更长，便于审计与 SLA 计算
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_ticket_message` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `ticket_id` BIGINT NOT NULL COMMENT '关联工单ID（mo_ticket.id）',
    `sender_type` VARCHAR(16) NOT NULL COMMENT 'USER 用户 / AGENT 客服 / SYSTEM 系统',
    `sender_id` BIGINT COMMENT '发送方ID（用户或管理员）',
    `sender_name` VARCHAR(64) COMMENT '发送方展示名（冗余，列表渲染更快）',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `content_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT 文本 / IMAGE 图片 / SYSTEM 系统提示',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_ticket_msg_ticket` (`ticket_id`, `create_time`),
    INDEX `idx_ticket_msg_type` (`sender_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单对话消息表';

-- 把历史工单的"原始用户诉求"补录为 USER 消息：content 字段（V20260722_03 已存）
-- 首条 SYSTEM 消息：工单创建
INSERT IGNORE INTO `mo_ticket_message` (`ticket_id`, `sender_type`, `sender_id`, `sender_name`, `content`, `content_type`, `create_time`)
SELECT
    t.id,
    'USER', t.user_id, t.user_name,
    IFNULL(t.content, CONCAT('用户提交工单: ', t.title)),
    'TEXT',
    t.create_time
FROM `mo_ticket` t
WHERE t.content IS NOT NULL OR t.title IS NOT NULL;
