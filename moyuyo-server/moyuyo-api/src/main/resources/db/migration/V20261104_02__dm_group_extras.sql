-- 群聊扩展：群公告、群头像
ALTER TABLE `mo_dm_conversation`
    ADD COLUMN `announcement` VARCHAR(500) NULL COMMENT '群公告（仅群聊）' AFTER `owner_id`,
    ADD COLUMN `avatar` VARCHAR(255) NULL COMMENT '群头像URL（仅群聊）' AFTER `announcement`;
