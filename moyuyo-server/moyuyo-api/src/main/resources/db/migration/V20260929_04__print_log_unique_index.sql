-- 给 mo_order_print_log 增加 (order_id, print_type, template_name, paper_size) 唯一索引
-- 配合 AdminOrderOpsServiceImpl.recordPrint 的 INSERT ... ON DUPLICATE KEY UPDATE 原子累加
--
-- 必须先清理历史重复行（老逻辑每次都 insert，无唯一约束，会产生大量重复），
-- 否则 ADD UNIQUE 会因存在重复键而失败。
--
-- 注意：MySQL DDL（ALTER TABLE）是隐式提交的，不能放在显式事务里。
--
-- 修复说明：
-- 原实现使用 TEMPORARY TABLE + 子查询别名 t 的写法，在 MySQL 5.7 上对空表
-- 或低版本驱动会触发 "Can't reopen table: 't'" 异常，导致整个 Flyway 脚本失败。
-- 现改用派生表（derived table）+ 自连接的纯 SELECT 子查询方案，兼容 MySQL 5.7 / 8.0，
-- 同时去掉对临时表的依赖，避免会话/连接复用导致的 reopen 问题。

-- 1) 删除非保留行（同 (order_id, print_type, template_name, paper_size) 分组下保留 id 最大的一行）
--    用派生表计算每个分组内待删除的 id 集合（除 max(id) 外的所有 id），
--    直接通过 IN 子查询 DELETE，避免 JOIN 临时表
DELETE mo
FROM mo_order_print_log mo
WHERE mo.id NOT IN (
  SELECT keep_id FROM (
    SELECT MAX(id) AS keep_id
    FROM mo_order_print_log
    GROUP BY order_id, print_type, template_name, paper_size
  ) AS g
);

-- 2) 用派生表聚合 print_count，更新到保留行的 print_count 字段
--    UPDATE ... JOIN (SELECT ...) 的派生表别名不能与被更新表别名同名，
--    这里用 agg 作为派生表别名，避免被 MySQL 误识别为外层引用。
UPDATE mo_order_print_log mo
JOIN (
  SELECT order_id, print_type, template_name, paper_size, SUM(print_count) AS total_count
  FROM mo_order_print_log
  GROUP BY order_id, print_type, template_name, paper_size
) AS agg
  ON  mo.order_id      = agg.order_id
  AND mo.print_type    = agg.print_type
  AND mo.template_name = agg.template_name
  AND mo.paper_size    = agg.paper_size
SET mo.print_count = agg.total_count;

-- 3) 加唯一索引（清理完重复行后才能加）
ALTER TABLE mo_order_print_log
  ADD UNIQUE KEY `uk_order_print_dim` (`order_id`, `print_type`, `template_name`, `paper_size`);