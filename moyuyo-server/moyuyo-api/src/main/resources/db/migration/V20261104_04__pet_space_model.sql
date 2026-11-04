-- ============================================================
-- V20261104_04__pet_space_model.sql
-- APP 宠物 3D 空间模型管理
-- 一种用途（房间场景 / 静态角色 / 行走角色）可登记多个版本，
-- 同时只允许一个 ENABLED；APP 进入 3D 空间时按用途拉取启用版本。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_pet_space_model` (
  `id`            BIGINT       NOT NULL                COMMENT '雪花ID',
  `model_key`     VARCHAR(32)  NOT NULL                COMMENT '用途：SCENE 房间场景 / AVATAR_IDLE 静态角色 / AVATAR_WALK 行走角色',
  `display_name`  VARCHAR(100) NOT NULL                COMMENT '模型名称',
  `file_url`      VARCHAR(512) NOT NULL                COMMENT '模型文件地址（/uploads/... 或 /static/...）',
  `file_format`   VARCHAR(16)  NOT NULL DEFAULT 'GLB'  COMMENT '文件格式：GLB/GLTF/FBX',
  `file_size`     BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
  `version`       VARCHAR(32)  NULL                    COMMENT '版本标识（便于运营区分）',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
  `remark`        VARCHAR(500) NULL                    COMMENT '备注',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_space_model_key_url` (`model_key`, `file_url`),
  KEY `idx_pet_space_model_key_status` (`model_key`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP 宠物 3D 空间模型';

-- 内置现有模型作为启用版本，保证上线后 APP 行为与改造前一致
INSERT IGNORE INTO `mo_pet_space_model`
  (`id`, `model_key`, `display_name`, `file_url`, `file_format`, `file_size`, `version`, `status`, `remark`)
VALUES
  (1, 'SCENE', '默认房间场景', '/static/models/home.glb', 'GLB', 26694868, 'v1', 'ENABLED', '内置默认场景'),
  (2, 'AVATAR_IDLE', '默认静态角色', '/static/models/0.fbx', 'FBX', 30445628, 'v1', 'ENABLED', '内置默认静态模型'),
  (3, 'AVATAR_WALK', '默认行走角色', '/static/models/1.fbx', 'FBX', 30436988, 'v1', 'ENABLED', '内置默认行走模型');

-- RBAC：为新资源补充内置角色权限（幂等）
SET @perm_id := (SELECT COALESCE(MAX(`id`), 0) FROM `mo_admin_permission`);

-- OPERATOR 运营管理员：3D 模型全操作
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, v.`resource`, v.`action`, NOW()
FROM `mo_admin_role` r
JOIN (
    SELECT 'pet-space-model' AS `resource`, 'view' AS `action`
    UNION ALL SELECT 'pet-space-model', 'create'
    UNION ALL SELECT 'pet-space-model', 'edit'
    UNION ALL SELECT 'pet-space-model', 'delete'
) v
WHERE r.`code` = 'OPERATOR'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = v.`resource` AND p.`action` = v.`action`
  );

-- VIEWER 数据查看员：只读
INSERT INTO `mo_admin_permission` (`id`, `role_id`, `resource`, `action`, `create_time`)
SELECT (@perm_id := @perm_id + 1), r.`id`, 'pet-space-model', 'view', NOW()
FROM `mo_admin_role` r
WHERE r.`code` = 'VIEWER'
  AND NOT EXISTS (
    SELECT 1 FROM `mo_admin_permission` p
    WHERE p.`role_id` = r.`id` AND p.`resource` = 'pet-space-model' AND p.`action` = 'view'
  );
