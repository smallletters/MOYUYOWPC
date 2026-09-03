-- ============================================================
-- V20260903_01__add_user_mission_cycle_date.sql
-- 补齐 mo_user_mission.cycle_date 字段
-- 背景：UserMissionEntity 增加了 cycleDate 字段用于"周期到期自动重置进度"，
--       但 V20260716_01__new_modules_tables.sql 建表时漏建，
--       导致 GET /api/v1/missions/grouped、/api/v1/missions/stats 报
--       "Unknown column 'cycle_date' in 'field list'" 并 500。
-- Service 层 toVo() 已能容忍 NULL（历史数据视为过期清零显示），所以该列可空。
-- ============================================================

ALTER TABLE `mo_user_mission`
    ADD COLUMN `cycle_date` DATE DEFAULT NULL
        COMMENT '任务进度所属周期:DAILY=今天,WEEKLY=本周一;过期时 Service 层自动重置 progress=0'
        AFTER `claimed`;

-- 给周期清理 / 周期查询加索引，便于未来按过期周期清理历史进度
CREATE INDEX `idx_user_mission_cycle_date` ON `mo_user_mission` (`cycle_date`);