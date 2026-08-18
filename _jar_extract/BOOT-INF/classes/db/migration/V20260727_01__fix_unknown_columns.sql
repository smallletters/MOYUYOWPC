-- ============================================================
-- V20260727_01__fix_unknown_columns.sql
-- 补齐 Mapper 实际使用但建表语句缺失的列：
--   1. mo_points_log        - 加 deleted 软删除标记
--   2. mo_risk_alert_config - 加 notify_users 通知人列表、status 启用状态
--   3. mo_admin_role        - 加 is_preset 预置角色标记
--   4. mo_order_item        - 加 create_time 创建时间
-- 注意：mo_points_log 的 created_at 与 mo_admin_permission 的 role_id 由 V20260727_02 补齐
-- ============================================================

-- 1. 积分流水表：补 deleted 列
ALTER TABLE `mo_points_log`
    ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记 0=未删 1=已删' AFTER `remark`;

-- 2. 风控告警配置表：补 notify_users 与 status
ALTER TABLE `mo_risk_alert_config`
    ADD COLUMN `notify_users` JSON NULL COMMENT '通知用户ID列表' AFTER `notify_channels`,
    ADD COLUMN `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用状态 1=启用 0=禁用' AFTER `notify_users`;

-- 3. 管理员角色表：补 is_preset 预置角色标记
ALTER TABLE `mo_admin_role`
    ADD COLUMN `is_preset` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否预置角色 1=预置 0=自定义' AFTER `status`;

-- 4. 订单明细表：补 create_time 创建时间
ALTER TABLE `mo_order_item`
    ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `subtotal`;

-- 索引补齐（如果不存在则添加）
CREATE INDEX IF NOT EXISTS `idx_points_log_deleted` ON `mo_points_log` (`deleted`);
CREATE INDEX IF NOT EXISTS `idx_risk_alert_status` ON `mo_risk_alert_config` (`status`);
CREATE INDEX IF NOT EXISTS `idx_admin_role_preset` ON `mo_admin_role` (`is_preset`);
CREATE INDEX IF NOT EXISTS `idx_order_item_create_time` ON `mo_order_item` (`create_time`);
