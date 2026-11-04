-- ============================================================
-- V20261103_02__dm_messages.sql
-- 用户间私信（Douyin 式私信）：会话表 + 消息表
--
-- 设计：
--   1. mo_dm_conversation 以「归一化的两人组合(user_a < user_b)」保证一对用户唯一会话
--   2. unread_a / unread_b 按接收方分别累计未读数，避免会话列表逐条 COUNT 消息
--   3. last_message_* 为冗余字段，列表页无需回表查消息
--   4. mo_dm_message 保存消息明细，read_flag 按接收方标记已读
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_dm_conversation` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_a` BIGINT NOT NULL COMMENT '参与者A（较小的用户ID）',
    `user_b` BIGINT NOT NULL COMMENT '参与者B（较大的用户ID）',
    `last_message_id` BIGINT NULL COMMENT '最后一条消息ID',
    `last_message_content` VARCHAR(500) NULL COMMENT '最后一条消息预览（截断）',
    `last_message_at` DATETIME NULL COMMENT '最后一条消息时间',
    `unread_a` INT NOT NULL DEFAULT 0 COMMENT 'A的未读数（B发来的消息）',
    `unread_b` INT NOT NULL DEFAULT 0 COMMENT 'B的未读数（A发来的消息）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_dm_conv_pair` (`user_a`, `user_b`),
    KEY `idx_dm_conv_a_time` (`user_a`, `last_message_at`),
    KEY `idx_dm_conv_b_time` (`user_b`, `last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信会话表';

CREATE TABLE IF NOT EXISTS `mo_dm_message` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `conversation_id` BIGINT NOT NULL COMMENT '关联会话ID（mo_dm_conversation.id）',
    `sender_id` BIGINT NOT NULL COMMENT '发送方用户ID',
    `receiver_id` BIGINT NOT NULL COMMENT '接收方用户ID',
    `content` VARCHAR(2000) NOT NULL COMMENT '文本内容（上限2000字）',
    `content_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT 文本',
    `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '接收方是否已读：0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_dm_msg_conv_time` (`conversation_id`, `create_time`, `id`),
    KEY `idx_dm_msg_receiver_read` (`receiver_id`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信消息表';
