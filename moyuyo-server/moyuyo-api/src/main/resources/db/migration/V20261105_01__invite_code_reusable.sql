-- ============================================================
-- V20261105_01__invite_code_reusable.sql
-- 邀请码改为「一人一码、可被多位好友复用」：
--   1) 去掉 invite_code 唯一约束，允许同一邀请码对应多条邀请关系记录
--   2) 为 invited_user_id 建索引，支撑「按被邀请人反查邀请关系」发放首单奖励
-- ============================================================

ALTER TABLE `mo_invite` DROP INDEX `invite_code`;

ALTER TABLE `mo_invite` ADD INDEX `idx_invited_user_id` (`invited_user_id`);
