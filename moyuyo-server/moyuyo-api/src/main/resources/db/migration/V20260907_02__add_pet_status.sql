-- ============================================================
-- V20260907_02__add_pet_status.sql
-- 宠物档案状态标记：为 mo_pet 增加 status 列，支持「去世/意外」等标记
-- 背景：宠物档案页原仅支持新增/编辑，缺少「去世/意外」等状态标记。
--      采用状态标记（软性保留档案，可查看）而非删除，故调整 status 列。
-- 注意：V2 建表时 mo_pet.status 已为 tinyint(1) DEFAULT 1（历史遗留，恒为 1），
--      因此这里不能 ADD COLUMN（会与既有列重名报错），而是对既有列做
--      类型转型（tinyint → VARCHAR 枚举）并归一化存量数据。
-- 枚举值：ACTIVE(正常) / DIED(去世) / ACCIDENT(意外) / MEDICAL(就医)，存量默认 ACTIVE。
-- ============================================================

-- 1. 存量兜底：NULL 统一置 1（历史默认即“正常”），避免转 NOT NULL 时失败
UPDATE mo_pet SET status = 1 WHERE status IS NULL;

-- 2. 既有 tinyint 列转型为状态枚举字符串列
ALTER TABLE mo_pet
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    COMMENT '宠物状态：ACTIVE正常/DIED去世/ACCIDENT意外/MEDICAL就医';

-- 3. 数据归一化：tinyint 1/0 → ACTIVE（转型后 MySQL 存为字符串 '1'/'0'）
UPDATE mo_pet SET status = 'ACTIVE' WHERE status IN ('1', '0');

-- 4. 供「按状态统计/筛选」使用（档案页、主页宠物列表会按状态标识）
ALTER TABLE mo_pet
  ADD INDEX idx_status (`status`);
