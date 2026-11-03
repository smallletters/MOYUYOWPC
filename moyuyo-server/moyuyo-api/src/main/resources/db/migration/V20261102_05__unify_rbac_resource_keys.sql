-- ============================================================
-- V20261102_05__unify_rbac_resource_keys.sql
-- 统一 RBAC 资源键：resource = 管理端接口 URL 首段（Controller @RequestMapping 首段）
-- 背景：AdminPermissionFilter 已移除「子模块 → 主资源」别名表，矩阵勾选与接口校验一一对应。
-- 本迁移补齐删除别名后可能丢失的内置角色权限，保证预设角色行为不退化。
-- ============================================================

-- 1. 清理无对应接口的 settlement 权限行（结算接口实际为 /api/admin/finance/settlements）
DELETE FROM `mo_admin_permission` WHERE `resource` = 'settlement';

-- 2. 为内置角色补齐权限（幂等：已存在则跳过）
SET @perm_id := (SELECT COALESCE(MAX(`id`), 0) FROM `mo_admin_permission`);

-- 2.1 OPERATOR 运营管理员：换货管理全操作、意见反馈查看/处理
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'exchanges' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'exchanges', 'create'
    UNION ALL SELECT 'exchanges', 'edit'
    UNION ALL SELECT 'exchanges', 'delete'
    UNION ALL SELECT 'feedback', 'view'
    UNION ALL SELECT 'feedback', 'edit'
) v
WHERE r.`code` = 'OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );

-- 2.2 CUSTOMER_SVC 客服：换货查看/处理、意见反馈查看/处理（原别名下通过 orders:view 访问，删别名后需独立授权）
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'exchanges' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'exchanges', 'edit'
    UNION ALL SELECT 'feedback', 'view'
    UNION ALL SELECT 'feedback', 'edit'
) v
WHERE r.`code` = 'CUSTOMER_SVC'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );

-- 2.3 FINANCE 财务：关税管理全操作、换货查看（原别名 tariff/exchange → finance/order）
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'tariff' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'tariff', 'create'
    UNION ALL SELECT 'tariff', 'edit'
    UNION ALL SELECT 'tariff', 'delete'
    UNION ALL SELECT 'exchanges', 'view'
) v
WHERE r.`code` = 'FINANCE'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );

-- 2.4 VIEWER 数据查看员：商品审批/商品分析/用户画像/换货只读（原别名归一到 product/user/order）
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'product-approval' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'product-analysis', 'view'
    UNION ALL SELECT 'user-profile', 'view'
    UNION ALL SELECT 'exchanges', 'view'
) v
WHERE r.`code` = 'VIEWER'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );
