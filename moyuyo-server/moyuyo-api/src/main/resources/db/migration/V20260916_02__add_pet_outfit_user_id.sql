-- ============================================================
-- V20260916_02__add_pet_outfit_user_id.sql
-- mo_pet_outfit 加 user_id 字段，区分系统默认装扮 vs 用户上传自定义形象
--
-- 背景：
-- 1) 原表只有 pet_id 字段，无法区分装扮归属（系统 vs 用户），
--    任何登录用户只要知道 petId + outfitId 就能装备/删除,
--    这是严重越权风险。
-- 2) 用户上传的"自定义形象"会被服务端与系统装扮混在一起,
--    业务上无法做"只删自己上传的"操作。
--
-- 修复：
-- 1) 新增 user_id BIGINT NULL：
--    - NULL = 系统默认装扮（运营 seed 数据,所有用户可见）
--    - 非 NULL = 用户上传的自定义形象（仅 owner 可见/可删）
-- 2) 索引 (user_id, pet_id) 支持"列出我的自定义装扮"查询
-- 3) PetDresserService 所有写操作（equip/delete）增加 userId 校验：
--    - 系统装扮任何登录用户可装备（社交体验）
--    - 用户装扮仅 owner 可装备/删除
-- ============================================================

-- 1) 添加 user_id 字段
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_pet_outfit' AND COLUMN_NAME='user_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_pet_outfit` ADD COLUMN `user_id` BIGINT NULL COMMENT ''所属用户(NULL=系统默认装扮,非NULL=用户上传)'' AFTER `pet_id`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 添加 (user_id, pet_id) 索引:支持"列出某用户某宠物的自定义装扮"
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_pet_outfit' AND INDEX_NAME='idx_pet_outfit_user_pet');
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE `mo_pet_outfit` ADD INDEX `idx_pet_outfit_user_pet` (`user_id`, `pet_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
