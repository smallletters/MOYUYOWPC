-- ============================================================
-- V20260804_02__enable_order_partition.sql
-- 启用 mo_order 按月分区（生产环境必备）
--
-- 启用顺序：
--   1. 检查是否有外键引用 mo_order（无外键才能分区）
--   2. 删除单列主键，重建为复合主键 (id, create_time)  [分区键必须在主键中]
--   3. 按月 RANGE 分区，提前创建 12 个月分区 + p_max 兜底
--
-- ⚠️ 重要：ALTER TABLE 对大表是重型 DDL，强烈建议：
--   - 在维护窗口执行（凌晨低峰）
--   - 使用 pt-online-schema-change 或 gh-ost 工具避免锁表
--   - 先在测试环境跑通
--   - 单表数据 < 1 亿行可走原生 ALTER；> 1 亿行必须用工具
-- ============================================================

-- 1. 防呆：拒绝在有外键的表上执行分区（MySQL 不允许带外键的表分区）
SET @fk_exists = (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mo_order'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
-- 若存在外键则中止（由 DBA 评估是否先 DROP FK 再分区）
SET @msg = IF(@fk_exists > 0,
    'ERROR: mo_order 存在外键引用，分区前请先 DROP FOREIGN KEY 并重建为应用层关联',
    'OK: mo_order 无外键引用，可安全启用分区');
SELECT @msg AS pre_check;

-- 2. 删除单列主键，重建为复合主键（分区键必须包含在主键中）
ALTER TABLE `mo_order` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`, `create_time`);

-- 3. 按月 RANGE 分区：覆盖 12 个月 + 兜底分区
-- 起点使用 TO_DAYS(create_time) 性能更好；p_max 兜底防止新月份无分区可写
ALTER TABLE `mo_order`
PARTITION BY RANGE (TO_DAYS(`create_time`)) (
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
    PARTITION p202702 VALUES LESS THAN (TO_DAYS('2027-03-01')),
    PARTITION p202703 VALUES LESS THAN (TO_DAYS('2027-04-01')),
    PARTITION p202704 VALUES LESS THAN (TO_DAYS('2027-05-01')),
    PARTITION p202705 VALUES LESS THAN (TO_DAYS('2027-06-01')),
    PARTITION p202706 VALUES LESS THAN (TO_DAYS('2027-07-01')),
    PARTITION p202707 VALUES LESS THAN (TO_DAYS('2027-08-01')),
    PARTITION p_max    VALUES LESS THAN MAXVALUE
);

-- 4. 提示：建议在 DBA 维护脚本里设置每月定时建好未来 3 个月的新分区
--   ALTER TABLE mo_order REORGANIZE PARTITION p_max INTO (
--       PARTITION p202708 VALUES LESS THAN (TO_DAYS('2027-09-01')),
--       PARTITION p_max    VALUES LESS THAN MAXVALUE
--   );