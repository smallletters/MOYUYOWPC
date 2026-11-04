-- ============================================================
-- V20261103_06__dm_quote_and_user_delete.sql
-- 私信：引用回复字段 + 用户级消息删除（仅自己隐藏）
--
-- 设计：
--   1. mo_dm_message 增加 quote_message_id / quote_content 冗余引用摘要，
--      列表直接返回，无需回表查询被引用消息
--   2. mo_dm_message_delete 记录「用户删除了某条消息」，只影响自己的会话视图，
--      对方消息记录保持不变
-- ============================================================

ALTER TABLE `mo_dm_message`
    ADD COLUMN `quote_message_id` BIGINT NULL COMMENT '引用回复的目标消息ID' AFTER `content_type`,
    ADD COLUMN `quote_content` VARCHAR(200) NULL COMMENT '引用消息摘要（冗余）' AFTER `quote_message_id`;

CREATE TABLE IF NOT EXISTS `mo_dm_message_delete` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '执行删除的用户ID',
    `message_id` BIGINT NOT NULL COMMENT '被删除的消息ID',
    `conversation_id` BIGINT NOT NULL COMMENT '所属会话ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_dm_msg_delete_user` (`user_id`, `message_id`),
    KEY `idx_dm_msg_delete_conv` (`user_id`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私信消息删除记录（仅对自己隐藏）';

-- 举报来源：mo_content_review 复用管理端审核队列，新增举报人字段便于审核与防重复
ALTER TABLE `mo_content_review`
    ADD COLUMN `reporter_id` BIGINT NULL COMMENT '举报人ID（用户举报时写入）' AFTER `user_id`,
    ADD KEY `idx_review_reporter` (`reporter_id`);

