-- ============================================================
-- V20260821_01__add_user_gender.sql
-- 给 mo_user 表新增 gender 字段（性别）
-- 用途：管理后台"用户画像"页面显示性别（男 / 女 / 中性 / 不透露）
-- 枚举：MALE / FEMALE / OTHER / UNDISCLOSED（与 AdminUserProfileController 对齐）
-- ============================================================

-- 1. 新增字段（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 守卫保持幂等）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='gender');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `mo_user` ADD COLUMN `gender` VARCHAR(16) NULL COMMENT ''性别：MALE(男)/FEMALE(女)/OTHER(中性)/UNDISCLOSED(不透露)'' AFTER `birthday`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 新增索引（便于按性别筛选 / 统计，MySQL 8 不支持 ADD INDEX IF NOT EXISTS）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND INDEX_NAME='idx_user_gender');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE `mo_user` ADD INDEX `idx_user_gender` (`gender`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;