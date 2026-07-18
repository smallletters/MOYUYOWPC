-- ============================================================
-- V20260718_05__search_visit_tables.sql
-- 搜索日志 / 访问日志 表
-- ============================================================

-- 1. 搜索日志表
CREATE TABLE IF NOT EXISTS `mo_search_log` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT COMMENT '用户ID',
    `keyword` VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    `result_count` INT DEFAULT 0 COMMENT '搜索结果数',
    `duration_ms` INT DEFAULT 0 COMMENT '搜索耗时(ms)',
    `ip` VARCHAR(64) COMMENT 'IP地址',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_keyword` (`keyword`),
    INDEX `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索日志表';

-- 2. 访问日志表
CREATE TABLE IF NOT EXISTS `mo_visit_log` (
    `id` BIGINT PRIMARY KEY,
    `user_id` BIGINT COMMENT '用户ID（未登录为NULL）',
    `session_id` VARCHAR(64) COMMENT '会话ID',
    `page_url` VARCHAR(500) COMMENT '页面URL',
    `page_name` VARCHAR(128) COMMENT '页面名称',
    `referrer` VARCHAR(500) COMMENT '来源URL',
    `stay_duration` INT DEFAULT 0 COMMENT '停留时长(秒)',
    `ip` VARCHAR(64) COMMENT 'IP地址',
    `user_agent` VARCHAR(500) COMMENT 'User-Agent',
    `channel` VARCHAR(32) COMMENT '渠道标识',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_create_time` (`create_time` DESC),
    INDEX `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';
