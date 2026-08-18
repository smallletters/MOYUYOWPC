-- ============================================================
-- V20260806_01__operation_log_partition_and_index.sql
-- 操作审计日志表（mo_operation_log）按月分区 + 索引优化
--
-- 背景：审计日志保留 90 天（GDPR 合规建议），单表日写入量 10w+ 时分区
--       能显著降低历史数据清理的代价（直接 DROP PARTITION 而非 DELETE）
--
-- 兼容性：
--   - 与 V20260804_03（建表脚本）配套，幂等
--   - 启用占位符 ${moyuyo_enable_partition}（默认 true）；
--     当 Flyway 传入 moyuyo_enable_partition=false（dev 默认）时，仅补充二级索引，不做分区
--
-- 风险：
--   - ALTER TABLE PARTITION BY 在已存在主键上要求主键包含分区键；
--     因此需要 DROP PRIMARY KEY 后重建为 (id, create_time)
--   - 大表 DDL 推荐使用 pt-online-schema-change / gh-ost 工具避免锁表
-- ============================================================

SET @enable_partition := IFNULL(NULLIF('${moyuyo_enable_partition}', ''), 'true');

-- 防呆：存在外键时拒绝分区（MySQL 不允许带外键的表分区）
SET @fk_exists := (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mo_operation_log'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @abort_sql := IF(@fk_exists > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''mo_operation_log 存在外键，禁止分区''',
    'DO 0');
PREPARE stmt FROM @abort_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 0. 索引补强：覆盖高频查询路径（即便不分区也应保留，对运维侧"按天清理"显著加速）
-- 使用 ADD INDEX 形式（MySQL 8.0.29+），更早版本请手工忽略已存在错误
ALTER TABLE `mo_operation_log` ADD INDEX `idx_oplog_success_time` (`success`, `create_time`);

-- 1. 仅在生产分区开关打开时重建复合主键
SET @step := IF(@enable_partition = 'true',
    'ALTER TABLE `mo_operation_log` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`, `create_time`)',
    'DO 0');
PREPARE stmt FROM @step; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 按月分区：覆盖当前月 + 后续 11 个月 + p_max 兜底
--    历史分区由维护脚本（scripts/maintenance/operation-log-partition-rollover.sh）按月 REORGANIZE p_max
SET @step := IF(@enable_partition = 'true',
    'ALTER TABLE `mo_operation_log` PARTITION BY RANGE (TO_DAYS(`create_time`)) (
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

SELECT IF(@enable_partition = 'true',
    'INFO: mo_operation_log 已启用月分区 + 复合主键',
    'INFO: moyuyo_enable_partition=false，已跳过 mo_operation_log 分区迁移（仅 dev）') AS partition_status;