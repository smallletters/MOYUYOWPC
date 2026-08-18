-- ============================================================
-- V20260722_02__create_cms_content.sql
-- CMS内容管理表（实体已存在但缺少建表脚本）
-- ============================================================

CREATE TABLE IF NOT EXISTS `mo_cms_content` (
    `id` BIGINT PRIMARY KEY COMMENT '雪花ID',
    `title` VARCHAR(255) NOT NULL COMMENT '标题',
    `type` VARCHAR(32) NOT NULL DEFAULT 'BANNER' COMMENT '类型：BANNER/RECOMMEND/TOPIC/PUSH',
    `content` TEXT COMMENT '内容描述',
    `image_url` VARCHAR(500) COMMENT '图片链接',
    `link_url` VARCHAR(500) COMMENT '跳转链接',
    `location` VARCHAR(32) DEFAULT '首页' COMMENT '展示位置',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/PAUSED',
    `sort_order` INT DEFAULT 0 COMMENT '排序权重',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `ctr` DOUBLE DEFAULT 0.0 COMMENT '点击率',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_type_status` (`type`, `status`),
    INDEX `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS内容管理表';

-- 插入一条默认示例数据
INSERT INTO `mo_cms_content` (`id`, `title`, `type`, `content`, `status`, `sort_order`, `create_time`)
VALUES (1, '欢迎使用MOYUYO管理后台', 'BANNER', '默认Banner内容，请在CMS管理中编辑', 'ACTIVE', 0, NOW())
ON DUPLICATE KEY UPDATE `title` = `title`;
