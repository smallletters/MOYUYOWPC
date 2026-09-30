-- 给 mo_logistics.order_id 加唯一索引：
--   配合 LogisticsService.shipOrder 的 INSERT IGNORE 幂等抢锁机制，
--   即使两个事务都通过了"幂等键"校验（例如不同运单号被 hash 出巧合的 idemKey），
--   也只能有一个事务成功插入 logistics 行；另一个会被唯一索引拒绝。
--
-- 必须先清理历史重复行（每个 order_id 只保留 1 行）：
--   保留规则：优先保留有 tracking_number 的；都没有就保留 id 最小的一行。
--
-- 注意：MySQL DDL（ALTER TABLE）是隐式提交的，不能放在显式事务里。
--
-- 修复说明：
-- 原实现使用 TEMPORARY TABLE + 子查询的写法，在 MySQL 5.7 / 部分驱动版本对空表或
-- 大数据量场景下会触发 "Can't reopen table" 异常，导致 Flyway 脚本失败。
-- 现改用纯派生表（derived table）+ 多步子查询方案，避免 TEMPORARY TABLE：
--   1) 用 UNION 把"有运单号保留 max(id)"和"无运单号兜底 min(id)"合并到一个保留 id 集合；
--   2) DELETE 通过 NOT IN 子查询清理非保留行；
--   3) ALTER TABLE 先删 idx_order_id 普通索引再加 uk_order_id 唯一索引。
--
-- 部署注意事项：
-- 该脚本假定 mo_logistics 上已存在 idx_order_id 普通索引（生产环境迁移前一定存在）；
-- 若数据库是手动重建的、运维已先删过 idx_order_id，本脚本会 DROP 报错，需先手动
-- 标记 Flyway schema_history 为 success（参考 run-migrations.ps1 的 repair 流程）。

-- 1) 删除非保留行：保留 id 不在"保留集合"内的行
--    保留集合 = 优先取每个 order_id 中存在 tracking_number 的最大 id；
--                其余 order_id 兜底取 min(id)。
--    派生表 g 通过 UNION 把两种来源合并，避免使用 TEMPORARY TABLE。
DELETE mo
FROM mo_logistics mo
WHERE mo.id NOT IN (
  SELECT keep_id FROM (
    -- 优先：有运单号则保留 max(id)
    SELECT MAX(id) AS keep_id, order_id
    FROM mo_logistics
    WHERE tracking_number IS NOT NULL AND tracking_number != ''
    GROUP BY order_id
    UNION
    -- 兜底：无运单号的 order_id 取 min(id)
    -- 用 LEFT JOIN 排除掉已经有运单号的，避免与上面重复
    SELECT MIN(id) AS keep_id, mo.order_id
    FROM mo_logistics mo
    LEFT JOIN (
      SELECT order_id
      FROM mo_logistics
      WHERE tracking_number IS NOT NULL AND tracking_number != ''
      GROUP BY order_id
    ) AS has_tracking ON mo.order_id = has_tracking.order_id
    WHERE has_tracking.order_id IS NULL
    GROUP BY mo.order_id
  ) AS g
);

-- 2) 加唯一索引：先删除原有普通索引 idx_order_id，再替换为唯一索引 uk_order_id
--    ALTER TABLE 语句自带隐式提交，每个语句独立执行（DDL 不能放事务）
ALTER TABLE mo_logistics DROP INDEX `idx_order_id`;
ALTER TABLE mo_logistics ADD UNIQUE KEY `uk_order_id` (`order_id`);