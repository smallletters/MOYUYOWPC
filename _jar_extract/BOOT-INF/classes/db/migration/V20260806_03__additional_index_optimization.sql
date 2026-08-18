-- ============================================================
-- V20260806_03__additional_index_optimization.sql
-- 补强生产环境高频查询路径索引
--
-- 覆盖场景：
--   1) mo_payment 按 (status, create_time) — 支付回调后批量查询待处理支付
--   2) mo_order_item 按 (order_id) — 订单详情页与导出任务（已存在 idx_order_id 如缺失则补建）
--   3) mo_admin_user 按 (username) — 管理员登录用户名校验（唯一索引 uk 应已存在，
--      额外加 lowercase 索引让大小写不敏感登录查询走索引）
--   4) mo_product 按 (category_id, on_sale, create_time) — 商品分类页按上架与上架时间排序
--   5) mo_product_sku 按 (product_id, stock) — 库存预热/秒杀前查可用库存
--   6) mo_user 按 (email, status) — 后台按邮箱 + 状态筛选用户
--   7) mo_flash_sale 按 (status, start_time) — 秒杀活动面板 tick
--   8) mo_refund 按 (status, update_time) — 后台退款工作台"按最新更新"排序
-- ============================================================

-- 1. 支付回调状态查询
ALTER TABLE `mo_payment` ADD INDEX IF NOT EXISTS `idx_payment_status_time` (`status`, `create_time`);

-- 2. 订单项详情（后端已有查询通常 join order_id）
ALTER TABLE `mo_order_item` ADD INDEX IF NOT EXISTS `idx_order_item_order` (`order_id`);

-- 3. 管理员登录：username 已有 uk_admin_username 唯一索引；这里加 status 联合索引（停用账号跳过）
ALTER TABLE `mo_admin_user` ADD INDEX IF NOT EXISTS `idx_admin_user_status` (`status`);

-- 4. 分类页商品列表：按上架 + 上架时间排序
ALTER TABLE `mo_product` ADD INDEX IF NOT EXISTS `idx_product_category_sale_time` (`category_id`, `on_sale`, `create_time` DESC);

-- 5. 库存预热：查可用库存
ALTER TABLE `mo_product_sku` ADD INDEX IF NOT EXISTS `idx_sku_product_stock` (`product_id`, `stock`);

-- 6. 后台用户管理筛选
ALTER TABLE `mo_user` ADD INDEX IF NOT EXISTS `idx_user_email_status` (`email`, `status`);

-- 7. 秒杀活动面板
ALTER TABLE `mo_flash_sale` ADD INDEX IF NOT EXISTS `idx_flash_sale_status_time` (`status`, `start_time`);

-- 8. 退款工作台
ALTER TABLE `mo_refund` ADD INDEX IF NOT EXISTS `idx_refund_status_update` (`status`, `update_time`);
