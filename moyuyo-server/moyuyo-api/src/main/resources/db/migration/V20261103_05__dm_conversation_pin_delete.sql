-- ============================================================
-- V20261103_05__dm_conversation_pin_delete.sql
-- 私信会话：双方各自独立的置顶 / 删除（隐藏）标记
--
-- 设计：
--   1. 置顶按用户独立维护（pinned_a / pinned_b），不互相影响
--   2. 删除只对当前用户隐藏会话（deleted_a / deleted_b），
--      消息明细与对方会话不受影响；收到新消息或主动打开时自动恢复
-- ============================================================

ALTER TABLE `mo_dm_conversation`
    ADD COLUMN `pinned_a` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'A是否置顶' AFTER `unread_b`,
    ADD COLUMN `pinned_b` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B是否置顶' AFTER `pinned_a`,
    ADD COLUMN `deleted_a` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'A是否删除（隐藏）会话' AFTER `pinned_b`,
    ADD COLUMN `deleted_b` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B是否删除（隐藏）会话' AFTER `deleted_a`;
