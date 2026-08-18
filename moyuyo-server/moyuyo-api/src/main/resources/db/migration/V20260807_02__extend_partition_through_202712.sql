-- ============================================================
-- V20260807_02__extend_partition_through_202712.sql
-- 扩展 mo_order / mo_operation_log 月分区到 2027-12
--
-- 背景：
--   V20260804_02 / V20260806_01 已预创建到 2027-07 的月分区，距离 2026-08 仅 11 个月窗口。
--   若运维侧每月 REORGANIZE PARTITION 滚动脚本（scripts/maintenance/order-partition-rollover.ps1）
--   错过执行，2027-08-01 当天新订单 / 审计写入会落入 p_max 兜底分区，长时间累积后
--   p_max 体积会爆炸（无法按月 DROP，且全表扫描成本上升）。
--
-- 风险评估：
--   - 单次 REORGANIZE PARTITION p_max INTO (...) 是 O(1) 数据字典变更，
--     不重建现有数据，本质上将 p_max 一分为二。
--   - 在线执行安全，但仍建议在低峰期执行（凌晨 2-4 点）。
--   - 脚本幂等：每个目标分区已存在则跳过，避免重复执行干扰。
--
-- 巩固窗口：本次扩展到 2027-12（约 17 个月），给运维 ~1 年 buffer 提前安排后续滚动。
-- ============================================================

-- 0. dev 跳过开关：dev profile 默认 moyuyo_enable_partition=false 时整体跳过
--    （dev 通常未启用分区，与 V20260804_02 / V20260806_01 行为一致）
SET @enable_partition := IFNULL(NULLIF('${moyuyo_enable_partition}', ''), 'true');

-- 1. 表是否存在 + 已分区的兜底检查（生产环境分区已生效，此处仅防御）
SET @order_partitioned := (
    SELECT COUNT(1) FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_order'
      AND PARTITION_NAME = 'p_max'
);
SET @oplog_partitioned := (
    SELECT COUNT(1) FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_operation_log'
      AND PARTITION_NAME = 'p_max'
);

-- 2. 准备 REORGANIZE 子句字符串（使用 CONCAT 拼接，MySQL 字符串函数）
--    5 个月分区上界：下月 1 号的 TO_DAYS
--    末尾 p_max 保留为尾巴
SET @new_partitions := CONCAT(
    'PARTITION p202708 VALUES LESS THAN (TO_DAYS(''2027-09-01'')), ',
    'PARTITION p202709 VALUES LESS THAN (TO_DAYS(''2027-10-01'')), ',
    'PARTITION p202710 VALUES LESS THAN (TO_DAYS(''2027-11-01'')), ',
    'PARTITION p202711 VALUES LESS THAN (TO_DAYS(''2027-12-01'')), ',
    'PARTITION p202712 VALUES LESS THAN (TO_DAYS(''2028-01-01'')), ',
    'PARTITION p_max    VALUES LESS THAN MAXVALUE'
);

-- 3. 幂等检查：若 5 个新分区已全部存在，则跳过（避免重复执行 DDL）
--    任一缺失才执行 REORGANIZE
SET @new_part_count := (
    SELECT COUNT(1) FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_order'
      AND PARTITION_NAME IN ('p202708', 'p202709', 'p202710', 'p202711', 'p202712')
);

-- 4. mo_order：REORGANIZE p_max 切分
--    仅在生产分区开启 + p_max 已存在 + 新分区尚未存在时执行
SET @order_step := IF(@enable_partition = 'true'
                      AND @order_partitioned > 0
                      AND @new_part_count < 5,
    CONCAT('ALTER TABLE mo_order REORGANIZE PARTITION p_max INTO (', @new_partitions, ')'),
    'DO 0');
PREPARE stmt FROM @order_step; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. mo_operation_log：同样幂等检查
SET @oplog_new_part_count := (
    SELECT COUNT(1) FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mo_operation_log'
      AND PARTITION_NAME IN ('p202708', 'p202709', 'p202710', 'p202711', 'p202712')
);
SET @oplog_step := IF(@enable_partition = 'true'
                      AND @oplog_partitioned > 0
                      AND @oplog_new_part_count < 5,
    CONCAT('ALTER TABLE mo_operation_log REORGANIZE PARTITION p_max INTO (', @new_partitions, ')'),
    'DO 0');
PREPARE stmt FROM @oplog_step; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. 验证：列出两张表的目标分区
SELECT TABLE_NAME, PARTITION_NAME, PARTITION_DESCRIPTION
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('mo_order', 'mo_operation_log')
  AND PARTITION_NAME IN ('p202708', 'p202709', 'p202710', 'p202711', 'p202712', 'p_max')
ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION;

-- 7. 提示：本次扩展后，下次滚动维护可继续按月追加（建议 2027-09 月初执行）
SELECT IF(@enable_partition = 'true',
    'INFO: mo_order / mo_operation_log 分区已扩展到 2027-12，下次维护建议 2027-09-01',
    'INFO: moyuyo_enable_partition=false，已跳过分区扩展（dev 模式）') AS migration_status;
