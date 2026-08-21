-- ============================================================
-- V20260821_01__add_user_gender.sql
-- 给 mo_user 表新增 gender 字段（性别）
-- 用途：管理后台"用户画像"页面显示性别（男 / 女 / 中性 / 不透露）
-- 枚举：MALE / FEMALE / OTHER / UNDISCLOSED（与 AdminUserProfileController 对齐）
-- ============================================================

-- 1. 新增字段（IF NOT EXISTS 幂等）
ALTER TABLE `mo_user`
    ADD COLUMN IF NOT EXISTS `gender` VARCHAR(16) NULL
        COMMENT '性别：MALE(男)/FEMALE(女)/OTHER(中性)/UNDISCLOSED(不透露)'
        AFTER `birthday`;

-- 2. 新增索引（便于按性别筛选 / 统计）
ALTER TABLE `mo_user`
    ADD INDEX IF NOT EXISTS `idx_user_gender` (`gender`);