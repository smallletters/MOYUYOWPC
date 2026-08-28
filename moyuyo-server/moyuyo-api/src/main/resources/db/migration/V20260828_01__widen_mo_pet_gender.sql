-- ============================================================
-- V20260828_01__widen_mo_pet_gender.sql
-- 宠物档案保存 409 修复：
-- mo_pet.gender 原为 TINYINT(1)（注释 1公/2母），前端传字符串 male/female
-- 导致 MySQL DataIntegrityViolation -> 409 数据冲突。
-- 改为 VARCHAR(16)，与 PetEntity.gender String 对齐，向前兼容历史 1/2 数值。
-- ============================================================

ALTER TABLE mo_pet
  MODIFY COLUMN gender VARCHAR(16) NULL COMMENT 'MALE/FEMALE/UNKNOWN 或历史数值 1/2';