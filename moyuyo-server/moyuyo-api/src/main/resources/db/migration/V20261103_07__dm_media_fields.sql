-- ============================================================
-- V20261103_07__dm_media_fields.sql
-- 私信：语音 / 文件消息附加字段
--
-- content 仍然存 URL；媒体元数据单独存列，避免把 JSON 塞进正文。
-- ============================================================

ALTER TABLE `mo_dm_message`
    ADD COLUMN `media_duration` INT NULL COMMENT '语音时长（秒）' AFTER `quote_content`,
    ADD COLUMN `media_name` VARCHAR(255) NULL COMMENT '文件名（文件消息）' AFTER `media_duration`,
    ADD COLUMN `media_size` BIGINT NULL COMMENT '文件大小（字节）' AFTER `media_name`;
