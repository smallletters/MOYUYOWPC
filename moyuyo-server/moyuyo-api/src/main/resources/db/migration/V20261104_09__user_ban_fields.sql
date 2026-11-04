-- ============================================================
-- V20261104_09__user_ban_fields.sql
-- 用户封禁细化：管理端封禁时记录原因与到期时间，C 端据此展示封禁提示。
--
-- 字段语义：
--   ban_reason      封禁理由（管理端必填）
--   banned_at       封禁操作时间
--   ban_expires_at  封禁到期时间（NULL = 永久封禁）
-- 到期后登录/刷新时会自动解封（status 恢复为 1 并清空封禁字段）。
-- ============================================================

ALTER TABLE `mo_user`
    ADD COLUMN `ban_reason` VARCHAR(255) NULL COMMENT '封禁原因（管理端填写）' AFTER `status`,
    ADD COLUMN `banned_at` DATETIME NULL COMMENT '封禁操作时间' AFTER `ban_reason`,
    ADD COLUMN `ban_expires_at` DATETIME NULL COMMENT '封禁到期时间（NULL=永久封禁）' AFTER `banned_at`;
