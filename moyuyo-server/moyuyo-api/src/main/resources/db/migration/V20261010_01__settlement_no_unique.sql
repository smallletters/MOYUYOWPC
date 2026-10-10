-- ============================================================
-- V20261010_01__settlement_no_unique.sql
-- 修复：mo_settlement.settlement_no 缺乏唯一约束
--
-- 背景：
--   结算单号生成规则为 SET-yyyyMMdd，同日多次创建会得到相同 settlementNo。
--   应用层 AdminFinanceController.createSettlement 已有 selectCount 防重，
--   但并发场景下两个请求可能同时通过 selectCount，再各自 INSERT，
--   会产生多条同 settlement_no 的结算 + 重复交易流水。
--
-- 本次：
--   在 settlement_no 上加唯一索引，由 MySQL 在写入阶段兜底并发冲突。
--   应用层 catch DuplicateKeyException 仍走"返回原记录"分支，行为不变。
--
-- 幂等说明：
--   所有 ALTER 使用 information_schema 判断 + PREPARE 动态执行，重跑不报错。
-- ============================================================

-- 1) 删除旧普通索引 idx_settlement_no（如果有），改为唯一索引
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_settlement'
      AND index_name = 'idx_settlement_no'
);
SET @sql := IF(@idx_exists > 0,
    'ALTER TABLE `mo_settlement` DROP INDEX `idx_settlement_no`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 加唯一索引 uk_settlement_no（如果已存在则跳过）
SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_settlement'
      AND index_name = 'uk_settlement_no'
);
SET @sql := IF(@uk_exists = 0,
    'ALTER TABLE `mo_settlement` ADD UNIQUE KEY `uk_settlement_no` (`settlement_no`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;