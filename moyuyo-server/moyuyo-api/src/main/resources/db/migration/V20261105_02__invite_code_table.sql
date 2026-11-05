-- ============================================================
-- V20261105_02__invite_code_table.sql
-- 邀请码独立成表：每个用户仅一条固定邀请码（user_id 唯一），邀请码不再随邀请次数漂移。
-- mo_invite 继续作为「邀请关系」表（invited_user_id 非空的行）。
--
-- 历史数据回填：按 user_id + id 升序取每个用户最早的邀请码，
-- 与旧逻辑（取最早一条记录展示）保持一致，老分享链接继续有效。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_invite_code` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `invite_code` VARCHAR(20) NOT NULL,
    `create_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户固定邀请码';

INSERT IGNORE INTO `mo_invite_code` (`id`, `user_id`, `invite_code`, `create_time`)
SELECT `id`, `user_id`, `invite_code`, `create_time`
FROM `mo_invite`
ORDER BY `user_id`, `id`;
