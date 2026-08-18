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
--
-- dev 环境兼容性：
--   启用占位符 ${moyuyo_enable_partition}（默认 true）。
--   当 Flyway 传入 moyuyo_enable_partition=false（dev 默认）时，本脚本不做任何 ALTER，
--   仅在 flyway_schema_history 留痕为 SKIPPED，避免破坏本地唯一索引。
-- ============================================================

-- 0. dev 跳过开关：占位符被 FlywayConfig 覆盖为 false 时跳过全部 ALTER
SET @enable_partition := IFNULL(NULLIF('${moyuyo_enable_partition}', ''), 'true');

-- 1. 防呆：拒绝在有外键的表上执行分区（MySQL 不允许带外键的表分区）
SET @fk_exists = (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mo_order'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
-- 若存在外键则真中止（使用 SIGNAL 抛出异常，阻断后续 DDL）
SET @msg = IF(@fk_exists > 0,
    'ERROR: mo_order 存在外键引用，分区前请先 DROP FOREIGN KEY 并重建为应用层关联',
    'OK: mo_order 无外键引用，可安全启用分区');
SELECT @msg AS pre_check;
-- 真中止：存在外键时抛 SQLSTATE 错误，Flyway 将回滚整个事务
SET @abort_sql = IF(@fk_exists > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''mo_order 存在外键，禁止分区''',
    'DO 0');
PREPARE abort_stmt FROM @abort_sql;
EXECUTE abort_stmt;
DEALLOCATE PREPARE abort_stmt;

-- 仅当 moyuyo_enable_partition=true 时执行破坏性 ALTER
SET @step := IF(@enable_partition = 'true',
    'ALTER TABLE `mo_order` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`, `create_time`)',
    'DO 0');
PREPARE stmt FROM @step; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @step := IF(@enable_partition = 'true',
    'ALTER TABLE `mo_order` PARTITION BY RANGE (TO_DAYS(`create_time`)) (
        PARTITION p202608 VALUES LESS THAN (TO_DAYS(''2026-09-01'')),
        PARTITION p202609 VALUES LESS THAN (TO_DAYS(''2026-10-01'')),
        PARTITION p202610 VALUES LESS THAN (TO_DAYS(''2026-11-01'')),
        PARTITION p202611 VALUES LESS THAN (TO_DAYS(''2026-12-01'')),
        PARTITION p202612 VALUES LESS THAN (TO_DAYS(''2027-01-01'')),
        PARTITION p202701 VALUES LESS THAN (TO_DAYS(''2027-02-01'')),
        PARTITION p202702 VALUES LESS THAN (TO_DAYS(''2027-03-01'')),
        PARTITION p202703 VALUES LESS THAN (TO_DAYS(''2027-04-01'')),
        PARTITION p202704 VALUES LESS THAN (TO_DAYS(''2027-05-01'')),
        PARTITION p202705 VALUES LESS THAN (TO_DAYS(''2027-06-01'')),
        PARTITION p202706 VALUES LESS THAN (TO_DAYS(''2027-07-01'')),
        PARTITION p202707 VALUES LESS THAN (TO_DAYS(''2027-08-01'')),
        PARTITION p_max    VALUES LESS THAN MAXVALUE
    )',
    'DO 0');
PREPARE stmt FROM @step; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 提示：建议在 DBA 维护脚本里设置每月定时建好未来 3 个月的新分区
--   ALTER TABLE mo_order REORGANIZE PARTITION p_max INTO (
--       PARTITION p202708 VALUES LESS THAN (TO_DAYS('2027-09-01')),
--       PARTITION p_max    VALUES LESS THAN MAXVALUE
--   );

-- dev 跳过模式下打日志便于排查
SELECT IF(@enable_partition = 'true',
    'INFO: mo_order 已启用月分区',
    'INFO: moyuyo_enable_partition=false，已跳过 mo_order 分区迁移（仅 dev）') AS partition_status;