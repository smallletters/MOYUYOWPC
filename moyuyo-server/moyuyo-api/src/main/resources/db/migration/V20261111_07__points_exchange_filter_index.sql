-- 积分兑换记录按关联订单状态筛选：为子查询补齐索引
-- mo_order：子查询条件 order_type = 'POINTS' AND status = ?
CREATE INDEX idx_order_type_status ON mo_order (order_type, status);
-- mo_points_exchange：外层按 order_id 过滤 / 关联订单展示
CREATE INDEX idx_points_exchange_order ON mo_points_exchange (order_id);
