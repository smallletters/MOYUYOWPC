-- 任务中心数据修补（配合 MissionServiceImpl 的周期/事务修正）
--
-- 1) 成就任务周期基准修正
--    代码侧 currentCycleDate 对 ACHIEVEMENT 统一返回固定基准日 1970-01-01
--    （见 MissionServiceImpl.ACHIEVEMENT_CYCLE）。
--    存量行的 cycle_date 是"上次触发当天"，若不同步成同一基准，代码上线后首次触发会被判定为
--    "换周期"，从而把「累计消费满 $500」「发布 10 条笔记」等已累计的进度清零。
UPDATE mo_user_mission um
JOIN mo_mission m ON m.id = um.mission_id
SET um.cycle_date = '1970-01-01'
WHERE m.type = 'ACHIEVEMENT';

-- 2) 清理并发首次触发可能产生的重复进度行（同 user_id + mission_id 只保留 id 最大的一条）
--    自连接是本表去重的标准写法（避免 "can't specify target table for update in FROM clause"）
DELETE um FROM mo_user_mission um
JOIN mo_user_mission keep
  ON keep.user_id = um.user_id
 AND keep.mission_id = um.mission_id
 AND keep.id > um.id;

-- 3) 加唯一约束，从根上避免重复进度行（否则 selectOne 会抛 TooManyResultsException）
ALTER TABLE mo_user_mission ADD UNIQUE KEY uk_user_mission (user_id, mission_id);

-- 4) 去掉列构成与新唯一索引完全重复的普通索引
ALTER TABLE mo_user_mission DROP INDEX idx_user_mission;
