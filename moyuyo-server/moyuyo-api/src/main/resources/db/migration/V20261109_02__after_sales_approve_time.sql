-- ============================================================
-- V20261109_02__after_sales_approve_time.sql
-- 售后单增加"审核通过时间"
-- 用途：超时未寄回自动关闭必须以上午审核时间计算；
--       原实现用 create_time/update_time 都不准确（wrapper 更新不会自动填充 update_time）。
-- ============================================================

ALTER TABLE `mo_after_sales`
    ADD COLUMN `approve_time` DATETIME NULL COMMENT '审核通过时间' AFTER `return_time`,
    ADD INDEX `idx_after_sales_status_approve` (`status`, `approve_time`);
