-- ============================================================
-- V20261030_01__upload_image_group.sql
-- 图片空间分组：支持运营按用途对上传图片分类，图片量大时快速筛选。
--
-- 设计说明：
--   1. 图片文件仍存储在本地/挂载卷（不上库），本表仅保存「图片 URL -> 分组」映射，
--      图片列表由 AdminUploadController 扫描目录得到后合并分组信息。
--   2. mo_upload_group 为分组定义；mo_upload_file 为图片与分组映射（url 唯一）。
--   3. 未建立映射的图片视为「未分组」，不强制回填，避免迁移期扫描文件系统。
--   4. 使用 CREATE TABLE IF NOT EXISTS 保证脚本可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_upload_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分组ID',
    `name` VARCHAR(64) NOT NULL COMMENT '分组名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值（越小越靠前）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片空间分组';

CREATE TABLE IF NOT EXISTS `mo_upload_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `url` VARCHAR(512) NOT NULL COMMENT '图片相对访问地址（如 /uploads/2026/10/30/xxx.png）',
    `group_id` BIGINT NULL COMMENT '所属分组ID，NULL 表示未分组',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_file_url` (`url`),
    KEY `idx_upload_file_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片空间图片与分组映射';
