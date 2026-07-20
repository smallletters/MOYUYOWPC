-- ============================================================
-- V20260720_02__fix_admin_rbac_tables.sql
-- 补齐管理后台缺失的 RBAC 权限相关表：
-- mo_admin_role（角色表）、mo_admin_permission（权限表）、
-- mo_admin_role_permission（角色权限关联表）
-- ============================================================

-- 1. 管理员角色表
CREATE TABLE IF NOT EXISTS `mo_admin_role` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    `description` VARCHAR(500) COMMENT '角色描述',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色表';

-- 2. 管理员权限表
CREATE TABLE IF NOT EXISTS `mo_admin_permission` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `name` VARCHAR(100) NOT NULL COMMENT '权限名称',
    `code` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    `type` VARCHAR(16) NOT NULL DEFAULT 'MENU' COMMENT 'MENU/BUTTON/API',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `path` VARCHAR(200) COMMENT '路由路径',
    `icon` VARCHAR(64) COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_parent` (`parent_id`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员权限表';

-- 3. 角色-权限关联表
CREATE TABLE IF NOT EXISTS `mo_admin_role_permission` (
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================================================
-- 种子数据：默认角色和权限
-- ============================================================

-- 插入默认角色
INSERT INTO `mo_admin_role` (`id`, `name`, `code`, `description`, `status`, `sort_order`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '拥有所有权限', 'ACTIVE', 1),
(2, '运营管理员', 'OPERATOR', '订单/商品/用户管理权限', 'ACTIVE', 2),
(3, '客服人员', 'CS_STAFF', '客服会话和工单权限', 'ACTIVE', 3),
(4, '财务人员', 'FINANCE', '财务相关权限', 'ACTIVE', 4),
(5, '数据查看员', 'VIEWER', '仅查看数据权限', 'ACTIVE', 5)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

-- 插入默认权限（一级菜单）
INSERT INTO `mo_admin_permission` (`id`, `name`, `code`, `type`, `parent_id`, `sort_order`) VALUES
(1, '仪表盘', 'DASHBOARD', 'MENU', 0, 1),
(10, '订单管理', 'ORDER', 'MENU', 0, 2),
(20, '商品管理', 'PRODUCT', 'MENU', 0, 3),
(30, '用户管理', 'USER', 'MENU', 0, 4),
(40, '营销管理', 'MARKETING', 'MENU', 0, 5),
(50, '内容管理', 'CMS', 'MENU', 0, 6),
(60, '客服管理', 'CS', 'MENU', 0, 7),
(70, '数据分析', 'ANALYTICS', 'MENU', 0, 8),
(80, '物流管理', 'LOGISTICS', 'MENU', 0, 9),
(90, '财务管理', 'FINANCE', 'MENU', 0, 10),
(100, '库存管理', 'INVENTORY', 'MENU', 0, 11),
(110, '系统设置', 'SYSTEM', 'MENU', 0, 12)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 超级管理员拥有所有权限
INSERT INTO `mo_admin_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `mo_admin_permission`
ON DUPLICATE KEY UPDATE `role_id` = `role_id`;
