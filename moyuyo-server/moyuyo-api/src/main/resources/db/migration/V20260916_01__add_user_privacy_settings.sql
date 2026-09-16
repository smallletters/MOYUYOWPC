-- ============================================================
-- V20260916_01__add_user_privacy_settings.sql
-- 隐私设置字段：4 个开关 + 1 个数据导出请求时间戳
-- 背景：/pages/user/privacy 页面需要真实读写后端,
-- 原 ProfileUpdateRequest 无对应字段,Bean Validation 静默忽略,
-- 导致前端 UI 切换但服务端无变化（隐私功能不可用）
--
-- 设计：
-- 1. 4 个开关默认值为隐私友好策略:
--    - public_favorites  : TRUE  (默认公开收藏,UX 友好)
--    - allow_view_profile : TRUE  (默认公开主页)
--    - show_online_status : FALSE (默认隐身,业内主流)
--    - allow_messages     : TRUE  (默认允许私信,社交通道)
-- 2. data_export_requested_at 记录最后一次导出请求时间,
--    与 mo_data_export_request 表配合做限流（同邮箱 1 天 1 次）
-- ============================================================

-- 1) public_favorites
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='public_favorites');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `public_favorites` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''公开我的收藏(隐私开关)'' AFTER `marketing_opt_in`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) allow_view_profile
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='allow_view_profile');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `allow_view_profile` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''允许他人查看主页(隐私开关)'' AFTER `public_favorites`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) show_online_status
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='show_online_status');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `show_online_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''显示在线状态(隐私开关,默认隐身)'' AFTER `allow_view_profile`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) allow_messages
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='allow_messages');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `allow_messages` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''允许私信(隐私开关)'' AFTER `show_online_status`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) data_export_requested_at：最后一次数据导出请求时间
-- 用于服务端限流（同账号 1 天最多 1 次，避免反复触发生成任务）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='data_export_requested_at');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `data_export_requested_at` DATETIME NULL COMMENT ''最后一次数据导出请求时间(限流用)'' AFTER `allow_messages`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6) 索引：按隐私字段做后台筛选（如"开启私信的用户"分析）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND INDEX_NAME='idx_user_privacy');
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `mo_user` ADD INDEX `idx_user_privacy` (`show_online_status`, `allow_messages`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
