-- ============================================================
-- V20260828_02__points_expire_time.sql
-- 积分有效期支持：
--   1. mo_points_log 新增 expire_time 列（DATETIME）
--   2. 历史存量流水的 expire_time 默认为 created_at + 12 月
--      （保证旧流水不会在迁移生效瞬间被一起清零）
--   3. 新增索引 idx_points_log_expire_time，支撑过期扫描 Job
-- ============================================================

-- 1. 新增 expire_time 列；存量默认为 created_at + INTERVAL 12 MONTH
ALTER TABLE `mo_points_log`
    ADD COLUMN `expire_time` DATETIME NULL COMMENT '过期时间（created_at + 12 月）' AFTER `created_at`;

UPDATE `mo_points_log`
SET `expire_time` = DATE_ADD(`created_at`, INTERVAL 12 MONTH)
WHERE `expire_time` IS NULL AND `created_at` IS NOT NULL;

-- 2. 索引：定时 Job 按 expire_time 升序扫描过期流水
CREATE INDEX `idx_points_log_expire_time` ON `mo_points_log` (`expire_time`);