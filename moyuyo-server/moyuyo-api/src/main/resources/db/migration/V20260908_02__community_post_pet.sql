-- ============================================================
-- V20260908_02__community_post_pet.sql
-- 社区帖子按宠物归属：允许发布时把图文帖关联到当前用户的某只宠物，
-- 供「宠物记忆树」按宠物聚合展示；历史帖子 pet_id 为空（普通帖子/种子数据）。
-- 与 CommunityPostEntity.petId 对齐。
-- ============================================================

ALTER TABLE mo_community_post
  ADD COLUMN pet_id BIGINT NULL COMMENT '关联宠物ID（NULL=普通帖子，不进入宠物记忆树）',
  ADD INDEX idx_post_pet (`pet_id`);
