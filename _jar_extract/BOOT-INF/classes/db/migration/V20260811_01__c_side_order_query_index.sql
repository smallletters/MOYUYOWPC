-- ============================================================
-- V20260811_01__c_side_order_query_index.sql
-- C 端订单/退款/售后高频查询路径索引补强
--
-- 业务背景：
--   OrderController.listOrders / RefundController / AfterSalesController 是用户最常访问的接口。
--   用户侧查询模式：(user_id, status=?, create_time DESC) + (user_id, create_time DESC)
--   管理侧查询模式：(status=?, update_time DESC) 已由 V20260806_02 覆盖
--
-- 本次新增覆盖：
--   1) mo_order：用户 + 状态 + 创建时间 DESC（订单中心"按状态筛选 + 默认排序"）
--   2) mo_refund：用户 + 退款状态（用户中心"我的退款"快速筛选）
--   3) mo_after_sales：用户 + 售后状态 + 创建时间 DESC（用户中心"我的售后"）
--   4) mo_cart：用户维度清理（cart 列表）
--   5) mo_address：用户维度（默认地址查询）
--   6) mo_user_behavior_event：(user_id, event_type, create_time DESC) - 用户行为聚合
--   7) mo_browsing_history：(user_id, create_time DESC) - 浏览记录列表
-- ============================================================

-- 1) 用户订单列表：C 端订单中心核心查询
ALTER TABLE `mo_order` ADD INDEX IF NOT EXISTS `idx_order_user_status_time` (`user_id`, `status`, `create_time`);

-- 2) 用户退款列表
ALTER TABLE `mo_refund` ADD INDEX IF NOT EXISTS `idx_refund_user_status` (`user_id`, `status`);

-- 3) 用户售后列表（如表存在则加；不存在则忽略）
ALTER TABLE `mo_after_sales` ADD INDEX IF NOT EXISTS `idx_after_sales_user_status_time` (`user_id`, `status`, `create_time`);

-- 4) 购物车：用户维度列表（清除失效商品等场景）
ALTER TABLE `mo_cart` ADD INDEX IF NOT EXISTS `idx_cart_user_selected` (`user_id`, `selected`);

-- 5) 收货地址：用户维度（默认地址查询 / 列表）
ALTER TABLE `mo_address` ADD INDEX IF NOT EXISTS `idx_address_user_default` (`user_id`, `is_default`);

-- 6) 用户行为事件聚合
ALTER TABLE `mo_user_behavior_event` ADD INDEX IF NOT EXISTS `idx_user_behavior_user_type_time` (`user_id`, `event_type`, `create_time`);

-- 7) 浏览记录
ALTER TABLE `mo_browsing_history` ADD INDEX IF NOT EXISTS `idx_browsing_user_time` (`user_id`, `create_time`);

-- 8) 支付幂等：用户维度快速查询
ALTER TABLE `mo_payment` ADD INDEX IF NOT EXISTS `idx_payment_user_time` (`user_id`, `create_time`);