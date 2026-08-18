-- ============================================================
-- V20260727_02__fix_more_unknown_columns.sql
-- 补齐上一轮 V20260727_01 之后仍然报错的列：
--   1. mo_points_log       - 加 created_at（实体字段 createdAt 映射）
--   2. mo_admin_permission - 加 role_id / resource / action
-- ============================================================

-- 1. 积分流水表：补 created_at 列
-- 实体 PointsLogEntity 使用 createdAt 字段，MyBatis-Plus 默认会映射到 created_at 列
ALTER TABLE `mo_points_log`
    ADD COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `deleted`;

-- 2. 管理员权限表：补 role_id / resource / action
-- 实体 AdminPermissionEntity 字段映射所需
ALTER TABLE `mo_admin_permission`
    ADD COLUMN `role_id` BIGINT NULL COMMENT '关联角色ID' AFTER `id`,
    ADD COLUMN `resource` VARCHAR(64) NULL COMMENT '资源编码' AFTER `name`,
    ADD COLUMN `action` VARCHAR(32) NULL COMMENT '操作类型' AFTER `resource`;

CREATE INDEX `idx_admin_permission_role` ON `mo_admin_permission` (`role_id`);
CREATE INDEX `idx_points_log_created_at` ON `mo_points_log` (`created_at`);
