-- ============================================================
-- V20260803_01__rbac_module_permissions.sql
-- 接口级 RBAC：按"角色 → 资源:操作"初始化默认权限矩阵
-- resource 为管理端 URL 模块段（/api/admin/{resource}），action: view/create/edit/delete
-- 注意：SUPER_ADMIN 由代码硬编码放行，无需数据行
-- ============================================================

-- 1. 统一角色编码：CS_STAFF 更名为 CUSTOMER_SVC，与管理员账号 role 取值保持一致
UPDATE `mo_admin_role` SET `code` = 'CUSTOMER_SVC' WHERE `code` = 'CS_STAFF';

-- 2. 清空旧模型权限数据（旧模型的 id/name/code/type 行与新模型不兼容）
DELETE FROM `mo_admin_permission`;

-- 2.1 兼容旧 schema：补齐新模型用到的列
-- 旧 mo_admin_permission 表 schema (V20260720_02) 没有 role_id/resource/action 字段，
-- name/code 为 NOT NULL。新模型仅使用 id/role_id/resource/action，缺字段会导致 INSERT 失败。
-- 此处幂等地添加缺失列并放宽 name/code 的 NOT NULL，避免全新库与已有库两条路径都能继续。
SET @col_role_id := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_admin_permission' AND COLUMN_NAME = 'role_id');
SET @ddl := IF(@col_role_id = 0,
    'ALTER TABLE `mo_admin_permission` ADD COLUMN `role_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''角色ID'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_resource := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_admin_permission' AND COLUMN_NAME = 'resource');
SET @ddl := IF(@col_resource = 0,
    'ALTER TABLE `mo_admin_permission` ADD COLUMN `resource` VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''资源编码'' AFTER `role_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_action := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_admin_permission' AND COLUMN_NAME = 'action');
SET @ddl := IF(@col_action = 0,
    'ALTER TABLE `mo_admin_permission` ADD COLUMN `action` VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''操作类型'' AFTER `resource`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 放宽 name/code 的 NOT NULL 约束，让新模型 INSERT 不再因缺值失败
ALTER TABLE `mo_admin_permission` MODIFY COLUMN `name` VARCHAR(100) NULL DEFAULT NULL COMMENT '权限名称';
ALTER TABLE `mo_admin_permission` MODIFY COLUMN `code` VARCHAR(100) NULL DEFAULT NULL COMMENT '权限编码';
-- 兼容旧表：放宽 type 默认值，避免 INSERT 时未指定 type 触发严格模式
ALTER TABLE `mo_admin_permission` MODIFY COLUMN `type` VARCHAR(16) NOT NULL DEFAULT 'API' COMMENT 'MENU/BUTTON/API';

-- 3. 默认角色权限矩阵
SET @role_operator := (SELECT id FROM mo_admin_role WHERE code = 'OPERATOR');
SET @role_cs := (SELECT id FROM mo_admin_role WHERE code = 'CUSTOMER_SVC');
SET @role_finance := (SELECT id FROM mo_admin_role WHERE code = 'FINANCE');
SET @role_viewer := (SELECT id FROM mo_admin_role WHERE code = 'VIEWER');

-- 3.1 OPERATOR 运营管理员：业务模块全操作；users 仅查看/编辑；仪表盘/数据分析仅查看
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`) VALUES
(1, @role_operator, 'dashboard', 'view', NOW()),
(2, @role_operator, 'analysis', 'view', NOW()),
(3, @role_operator, 'users', 'view', NOW()),
(4, @role_operator, 'users', 'edit', NOW()),
(5, @role_operator, 'products', 'view', NOW()), (6, @role_operator, 'products', 'create', NOW()), (7, @role_operator, 'products', 'edit', NOW()), (8, @role_operator, 'products', 'delete', NOW()),
(9, @role_operator, 'product-approval', 'view', NOW()), (10, @role_operator, 'product-approval', 'create', NOW()), (11, @role_operator, 'product-approval', 'edit', NOW()), (12, @role_operator, 'product-approval', 'delete', NOW()),
(13, @role_operator, 'product-analysis', 'view', NOW()), (14, @role_operator, 'product-analysis', 'create', NOW()), (15, @role_operator, 'product-analysis', 'edit', NOW()), (16, @role_operator, 'product-analysis', 'delete', NOW()),
(17, @role_operator, 'orders', 'view', NOW()), (18, @role_operator, 'orders', 'create', NOW()), (19, @role_operator, 'orders', 'edit', NOW()), (20, @role_operator, 'orders', 'delete', NOW()),
(21, @role_operator, 'order-ops', 'view', NOW()), (22, @role_operator, 'order-ops', 'create', NOW()), (23, @role_operator, 'order-ops', 'edit', NOW()), (24, @role_operator, 'order-ops', 'delete', NOW()),
(25, @role_operator, 'order-tags', 'view', NOW()), (26, @role_operator, 'order-tags', 'create', NOW()), (27, @role_operator, 'order-tags', 'edit', NOW()), (28, @role_operator, 'order-tags', 'delete', NOW()),
(29, @role_operator, 'user-profile', 'view', NOW()), (30, @role_operator, 'user-profile', 'create', NOW()), (31, @role_operator, 'user-profile', 'edit', NOW()), (32, @role_operator, 'user-profile', 'delete', NOW()),
(33, @role_operator, 'blacklist', 'view', NOW()), (34, @role_operator, 'blacklist', 'create', NOW()), (35, @role_operator, 'blacklist', 'edit', NOW()), (36, @role_operator, 'blacklist', 'delete', NOW()),
(37, @role_operator, 'crm', 'view', NOW()), (38, @role_operator, 'crm', 'create', NOW()), (39, @role_operator, 'crm', 'edit', NOW()), (40, @role_operator, 'crm', 'delete', NOW()),
(41, @role_operator, 'marketing', 'view', NOW()), (42, @role_operator, 'marketing', 'create', NOW()), (43, @role_operator, 'marketing', 'edit', NOW()), (44, @role_operator, 'marketing', 'delete', NOW()),
(45, @role_operator, 'coupons', 'view', NOW()), (46, @role_operator, 'coupons', 'create', NOW()), (47, @role_operator, 'coupons', 'edit', NOW()), (48, @role_operator, 'coupons', 'delete', NOW()),
(49, @role_operator, 'flash-sales', 'view', NOW()), (50, @role_operator, 'flash-sales', 'create', NOW()), (51, @role_operator, 'flash-sales', 'edit', NOW()), (52, @role_operator, 'flash-sales', 'delete', NOW()),
(53, @role_operator, 'live', 'view', NOW()), (54, @role_operator, 'live', 'create', NOW()), (55, @role_operator, 'live', 'edit', NOW()), (56, @role_operator, 'live', 'delete', NOW()),
(57, @role_operator, 'push', 'view', NOW()), (58, @role_operator, 'push', 'create', NOW()), (59, @role_operator, 'push', 'edit', NOW()), (60, @role_operator, 'push', 'delete', NOW()),
(61, @role_operator, 'sms', 'view', NOW()), (62, @role_operator, 'sms', 'create', NOW()), (63, @role_operator, 'sms', 'edit', NOW()), (64, @role_operator, 'sms', 'delete', NOW()),
(65, @role_operator, 'review', 'view', NOW()), (66, @role_operator, 'review', 'create', NOW()), (67, @role_operator, 'review', 'edit', NOW()), (68, @role_operator, 'review', 'delete', NOW()),
(69, @role_operator, 'content-review', 'view', NOW()), (70, @role_operator, 'content-review', 'create', NOW()), (71, @role_operator, 'content-review', 'edit', NOW()), (72, @role_operator, 'content-review', 'delete', NOW()),
(73, @role_operator, 'cms', 'view', NOW()), (74, @role_operator, 'cms', 'create', NOW()), (75, @role_operator, 'cms', 'edit', NOW()), (76, @role_operator, 'cms', 'delete', NOW()),
(77, @role_operator, 'knowledge-base', 'view', NOW()), (78, @role_operator, 'knowledge-base', 'create', NOW()), (79, @role_operator, 'knowledge-base', 'edit', NOW()), (80, @role_operator, 'knowledge-base', 'delete', NOW()),
(81, @role_operator, 'cs-sessions', 'view', NOW()), (82, @role_operator, 'cs-sessions', 'create', NOW()), (83, @role_operator, 'cs-sessions', 'edit', NOW()), (84, @role_operator, 'cs-sessions', 'delete', NOW()),
(85, @role_operator, 'ticket', 'view', NOW()), (86, @role_operator, 'ticket', 'create', NOW()), (87, @role_operator, 'ticket', 'edit', NOW()), (88, @role_operator, 'ticket', 'delete', NOW()),
(89, @role_operator, 'complaint', 'view', NOW()), (90, @role_operator, 'complaint', 'create', NOW()), (91, @role_operator, 'complaint', 'edit', NOW()), (92, @role_operator, 'complaint', 'delete', NOW()),
(93, @role_operator, 'satisfaction', 'view', NOW()), (94, @role_operator, 'satisfaction', 'create', NOW()), (95, @role_operator, 'satisfaction', 'edit', NOW()), (96, @role_operator, 'satisfaction', 'delete', NOW()),
(97, @role_operator, 'logistics', 'view', NOW()), (98, @role_operator, 'logistics', 'create', NOW()), (99, @role_operator, 'logistics', 'edit', NOW()), (100, @role_operator, 'logistics', 'delete', NOW()),
(101, @role_operator, 'inventory', 'view', NOW()), (102, @role_operator, 'inventory', 'create', NOW()), (103, @role_operator, 'inventory', 'edit', NOW()), (104, @role_operator, 'inventory', 'delete', NOW()),
(105, @role_operator, 'inventory-transfer', 'view', NOW()), (106, @role_operator, 'inventory-transfer', 'create', NOW()), (107, @role_operator, 'inventory-transfer', 'edit', NOW()), (108, @role_operator, 'inventory-transfer', 'delete', NOW()),
(109, @role_operator, 'price', 'view', NOW()), (110, @role_operator, 'price', 'create', NOW()), (111, @role_operator, 'price', 'edit', NOW()), (112, @role_operator, 'price', 'delete', NOW()),
(113, @role_operator, 'tariff', 'view', NOW()), (114, @role_operator, 'tariff', 'create', NOW()), (115, @role_operator, 'tariff', 'edit', NOW()), (116, @role_operator, 'tariff', 'delete', NOW()),
(117, @role_operator, 'points', 'view', NOW()), (118, @role_operator, 'points', 'create', NOW()), (119, @role_operator, 'points', 'edit', NOW()), (120, @role_operator, 'points', 'delete', NOW());

-- 3.2 CUSTOMER_SVC 客服：会话/工单/投诉/满意度/知识库全操作；订单/用户/物流/评价查看
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`) VALUES
(121, @role_cs, 'dashboard', 'view', NOW()),
(122, @role_cs, 'orders', 'view', NOW()),
(123, @role_cs, 'users', 'view', NOW()),
(124, @role_cs, 'user-profile', 'view', NOW()),
(125, @role_cs, 'logistics', 'view', NOW()),
(126, @role_cs, 'review', 'view', NOW()),
(127, @role_cs, 'cs-sessions', 'view', NOW()), (128, @role_cs, 'cs-sessions', 'create', NOW()), (129, @role_cs, 'cs-sessions', 'edit', NOW()), (130, @role_cs, 'cs-sessions', 'delete', NOW()),
(131, @role_cs, 'ticket', 'view', NOW()), (132, @role_cs, 'ticket', 'create', NOW()), (133, @role_cs, 'ticket', 'edit', NOW()), (134, @role_cs, 'ticket', 'delete', NOW()),
(135, @role_cs, 'complaint', 'view', NOW()), (136, @role_cs, 'complaint', 'create', NOW()), (137, @role_cs, 'complaint', 'edit', NOW()), (138, @role_cs, 'complaint', 'delete', NOW()),
(139, @role_cs, 'satisfaction', 'view', NOW()), (140, @role_cs, 'satisfaction', 'create', NOW()), (141, @role_cs, 'satisfaction', 'edit', NOW()), (142, @role_cs, 'satisfaction', 'delete', NOW()),
(143, @role_cs, 'knowledge-base', 'view', NOW()), (144, @role_cs, 'knowledge-base', 'create', NOW()), (145, @role_cs, 'knowledge-base', 'edit', NOW()), (146, @role_cs, 'knowledge-base', 'delete', NOW());

-- 3.3 FINANCE 财务：财务/结算/退款全操作；订单/仪表盘/数据分析查看
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`) VALUES
(147, @role_finance, 'dashboard', 'view', NOW()),
(148, @role_finance, 'analysis', 'view', NOW()),
(149, @role_finance, 'orders', 'view', NOW()),
(150, @role_finance, 'finance', 'view', NOW()), (151, @role_finance, 'finance', 'create', NOW()), (152, @role_finance, 'finance', 'edit', NOW()), (153, @role_finance, 'finance', 'delete', NOW()),
(154, @role_finance, 'settlement', 'view', NOW()), (155, @role_finance, 'settlement', 'create', NOW()), (156, @role_finance, 'settlement', 'edit', NOW()), (157, @role_finance, 'settlement', 'delete', NOW()),
(158, @role_finance, 'refunds', 'view', NOW()), (159, @role_finance, 'refunds', 'create', NOW()), (160, @role_finance, 'refunds', 'edit', NOW()), (161, @role_finance, 'refunds', 'delete', NOW());

-- 3.4 VIEWER 数据查看员：核心模块只读
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`) VALUES
(162, @role_viewer, 'dashboard', 'view', NOW()),
(163, @role_viewer, 'analysis', 'view', NOW()),
(164, @role_viewer, 'products', 'view', NOW()),
(165, @role_viewer, 'orders', 'view', NOW()),
(166, @role_viewer, 'users', 'view', NOW());
