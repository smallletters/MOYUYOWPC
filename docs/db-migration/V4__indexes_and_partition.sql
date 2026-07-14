-- ============================================================
-- V4__indexes_and_partition.sql
-- 性能优化：补充索引 + 订单表按时间分区（可选）
-- ============================================================

-- 1. 订单按月分区（生产环境建议启用，单表 > 1000 万行时考虑）
-- 注意：分区前需确认表已存在且无外键引用
-- ALTER TABLE mo_order PARTITION BY RANGE (TO_DAYS(create_time)) (
--   PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
--   PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
--   PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
--   PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
--   PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
--   PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
--   PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
--   PARTITION p_max    VALUES LESS THAN MAXVALUE
-- );

-- 2. 复合索引补充
CREATE INDEX idx_product_on_sale_category ON mo_product(on_sale, category_id);
CREATE INDEX idx_order_user_create        ON mo_order(user_id, create_time DESC);
CREATE INDEX idx_order_status_create     ON mo_order(status, create_time DESC);
CREATE INDEX idx_login_log_success       ON mo_login_log(success, login_time);

-- 3. 同步日志复合索引（便于失败任务扫描）
CREATE INDEX idx_sync_status_retry ON mo_sync_log(status, retry_count, create_time);

-- 4. 社区帖热门查询
CREATE INDEX idx_post_status_create ON mo_community_post(status, create_time DESC);

-- 5. 退款订单查询
CREATE INDEX idx_refund_status_create ON mo_refund(status, create_time);
