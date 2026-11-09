-- ============================================================
-- V20261105_04__woocommerce_permission.sql
-- 为管理端 WooCommerce 接口补充 RBAC 资源行。
-- 接口路径：/api/admin/woocommerce/**
-- GET  → view   （连接测试、商品/分类/订单查询）
-- POST → create （同步商品、同步订单）
-- 仅授予 OPERATOR 运营角色；SUPER_ADMIN 在过滤器内直接放行，无需落库。
-- 幂等：已存在同 role_id/resource/action 的行不会重复插入。
-- ============================================================

SET @perm_id := (SELECT COALESCE(MAX(`id`), 0) FROM `mo_admin_permission`);

INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'woocommerce' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'woocommerce', 'create'
) v
WHERE r.`code` = 'OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );
