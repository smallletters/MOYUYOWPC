-- ============================================================
-- V20260819_02__add_user_registration_channel.sql
-- 给 mo_user 表新增 registration_channel 字段（注册渠道）
-- 用途：管理后台"用户管理"页面"注册渠道"过滤器实际生效
-- 渠道枚举：web / app / wechat （与前端 AdminUserController.normalizeEnum 白名单一致）
-- ============================================================

-- 1. 新增字段（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 守卫保持幂等）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='registration_channel');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `mo_user` ADD COLUMN `registration_channel` VARCHAR(16) NULL COMMENT ''注册渠道：web/app/wechat（与 AdminUserController 白名单对齐）'' AFTER `country`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 新增索引（便于渠道筛选查询，MySQL 8 不支持 ADD INDEX IF NOT EXISTS）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND INDEX_NAME='idx_user_registration_channel');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE `mo_user` ADD INDEX `idx_user_registration_channel` (`registration_channel`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. seed 数据回填（幂等）
-- 现有8 位用户：alice/bob/charlie/diana/eva/frank/grace/henry/iris/jack 中
-- 给 web/app/wechat 三类各分配一些，避免单一渠道
UPDATE `mo_user` SET `registration_channel` = 'web'     WHERE `email` = 'alice@example.com'   AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'web'     WHERE `email` = 'bob@example.com'     AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'app'     WHERE `email` = 'charlie@example.co.uk' AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'wechat'  WHERE `email` = 'diana@example.de'    AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'app'     WHERE `email` = 'eva@example.fr'      AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'web'     WHERE `email` = 'frank@example.jp'    AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'wechat'  WHERE `email` = 'henry@example.ca'    AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'app'     WHERE `email` = 'iris@example.sg'     AND `registration_channel` IS NULL;
UPDATE `mo_user` SET `registration_channel` = 'web'     WHERE `email` = 'jack@example.com'    AND `registration_channel` IS NULL;