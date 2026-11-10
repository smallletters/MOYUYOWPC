-- ============================================================
-- V20261110_01__inventory_transfer_covering_index.sql
-- 各仓库存总览的在途聚合优化
--
-- 查询：SELECT to_warehouse_id, SUM(quantity) FROM mo_inventory_transfer
--       WHERE status = 'IN_TRANSIT' GROUP BY to_warehouse_id
-- 现状：仅有 idx_status(status)，需回表读取 to_warehouse_id / quantity
-- 方案：补充 (status, to_warehouse_id, quantity) 覆盖索引，避免回表
-- 幂等：先查 information_schema，已存在则跳过
-- ============================================================

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_inventory_transfer'
      AND index_name = 'idx_transfer_status_to'
);

SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `mo_inventory_transfer` ADD INDEX `idx_transfer_status_to` (`status`, `to_warehouse_id`, `quantity`)',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
