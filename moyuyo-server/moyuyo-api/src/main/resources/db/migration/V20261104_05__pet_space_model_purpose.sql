-- ============================================================
-- V20261104_05__pet_space_model_purpose.sql
-- APP 宠物 3D 空间模型用途字典
-- 用途用于区分模型类别（房间场景 / 静态角色 / 行走角色等），
-- 每个用途声明允许的模型文件格式；后台可新增自定义用途。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_pet_space_model_purpose` (
  `id`               BIGINT       NOT NULL                COMMENT '雪花ID',
  `purpose_key`      VARCHAR(32)  NOT NULL                COMMENT '用途标识（大写字母/数字/下划线，创建后不可修改）',
  `purpose_name`     VARCHAR(64)  NOT NULL                COMMENT '用途名称',
  `allowed_formats`  VARCHAR(64)  NOT NULL DEFAULT 'GLB'  COMMENT '允许的文件格式，逗号分隔：GLB,GLTF,FBX',
  `description`      VARCHAR(255) NULL                    COMMENT '用途说明',
  `sort_order`       INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
  `enabled`          TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '1启用 0停用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_space_model_purpose_key` (`purpose_key`),
  KEY `idx_pet_space_model_purpose_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP 宠物 3D 空间模型用途';

-- 内置用途：与 APP 引擎当前读取的 modelKey 保持一致
INSERT IGNORE INTO `mo_pet_space_model_purpose`
  (`id`, `purpose_key`, `purpose_name`, `allowed_formats`, `description`, `sort_order`, `enabled`)
VALUES
  (1, 'SCENE', '房间场景', 'GLB,GLTF', '3D 空间的房间/场景模型，APP 使用 GLTFLoader 加载', 10, 1),
  (2, 'AVATAR_IDLE', '静态角色', 'FBX', '角色待机模型，APP 使用 FBXLoader 加载', 20, 1),
  (3, 'AVATAR_WALK', '行走角色', 'FBX', '角色行走模型，APP 使用 FBXLoader 加载', 30, 1);
