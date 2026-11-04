-- 群聊会话：会话表增加类型/群名/群主，单聊参与者改为可空；新增群成员表
ALTER TABLE `mo_dm_conversation`
    MODIFY COLUMN `user_a` BIGINT NULL COMMENT '参与者A（单聊较小用户ID；群聊为 NULL）',
    MODIFY COLUMN `user_b` BIGINT NULL COMMENT '参与者B（单聊较大用户ID；群聊为 NULL）',
    ADD COLUMN `type` VARCHAR(8) NOT NULL DEFAULT 'SINGLE' COMMENT '会话类型：SINGLE 单聊 / GROUP 群聊' AFTER `id`,
    ADD COLUMN `name` VARCHAR(50) NULL COMMENT '群名称（仅群聊）' AFTER `type`,
    ADD COLUMN `owner_id` BIGINT NULL COMMENT '群主用户ID（仅群聊）' AFTER `name`;

CREATE TABLE `mo_dm_group_member` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `conversation_id` BIGINT NOT NULL COMMENT '群会话ID',
    `user_id` BIGINT NOT NULL COMMENT '成员用户ID',
    `unread` INT NOT NULL DEFAULT 0 COMMENT '该成员未读数',
    `pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶该群',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除（隐藏）该群',
    `role` VARCHAR(8) NOT NULL DEFAULT 'MEMBER' COMMENT '群内角色：OWNER / MEMBER',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dm_group_member` (`conversation_id`, `user_id`),
    KEY `idx_dm_group_member_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '群聊会话成员';
