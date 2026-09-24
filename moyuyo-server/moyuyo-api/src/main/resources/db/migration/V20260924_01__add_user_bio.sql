-- ============================================================
-- V20260924_01__add_user_bio.sql
-- 用户个人简介字段 bio：长按/点击编辑保存
-- 背景：community/profile 页面用户卡"简介"区域当前显示默认文案,
--      且 UserController 中 bio 写死返回空串,用户无法真实修改。
--      本次为 mo_user 表新增 bio 字段,后端写入/读取走真实列。
--
-- 设计：
-- 1. VARCHAR(200)：与 ProfileUpdateRequest @Size(max=200) 对齐,
--    兼顾微博式短简介场景,避免超长文本撑爆个人主页卡片
-- 2. NULL 默认：未填写 bio 时,前端按默认文案展示("这个人很懒,什么也没留下~")
--    NULL 与 "" 语义略有差异,但前端都按"空"处理,这里统一 NULL
-- 3. 兼容已存在数据：列不存在才 ADD,允许在已有 DB 上重复执行
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mo_user' AND COLUMN_NAME='bio');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `bio` VARCHAR(200) NULL DEFAULT NULL COMMENT ''用户简介(个人主页展示)'' AFTER `gender`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;