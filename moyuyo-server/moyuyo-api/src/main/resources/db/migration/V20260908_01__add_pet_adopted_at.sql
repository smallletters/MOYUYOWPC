-- ============================================================
-- V20260908_01__add_pet_adopted_at.sql
-- 宠物档案新增「加入家庭时间」：用于记录宠物何时被接入/加入家庭。
-- 与 PetEntity.adoptedAt(LocalDate) 对应，仅展示与档案编辑，默认空。
-- ============================================================

ALTER TABLE mo_pet
  ADD COLUMN adopted_at DATE NULL COMMENT '加入家庭时间';
