-- ============================================================
-- V4__indexes_and_partition.sql
-- 性能优化：补充索引
-- 订单表按月分区已迁移到 V20260804_02（生产环境必备）
-- ============================================================
-- ⚠️ 订单表分区已移至 V20260804_02，本迁移仅保留补充索引。
-- 部署生产前务必评估是否启用分区，单表超过 1000 万行时强烈建议。

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
