-- ============================================================
-- V20261008_02__fix_shipping_rate_unique.sql
-- 修复 P1：mo_shipping_rate 唯一索引设计错误
--
-- 旧设计问题：UNIQUE (zone_id, method_id, status)
--   - 把 status 也加进唯一键，导致 (zone, method, INACTIVE) 也唯一
--   - 运营想把 INACTIVE 改回 ACTIVE 时，与库里现有 INACTIVE 冲突
--   - 旧设计导致 service 层的"先停用再新增"模式不可用
--
-- 新设计：
--   1) 增加虚拟列 active_flag = IF(status='ACTIVE', 1, NULL)
--      NULL 不参与唯一约束（MySQL 行为），所以 INACTIVE 可有多条
--   2) UNIQUE (zone_id, method_id, active_flag) 只约束 ACTIVE
--   3) 应用层 selectCount(ACTIVE) 先做软检查，再 insert 触发 unique key
--      双重保护避免竞态（两个并发 insert 都通过 selectCount 后再撞 unique）
--
-- 幂等说明：
--   - 所有 ALTER 操作使用 IF EXISTS / 过程式判断，避免重跑报错。
--   - 当 Flyway 重跑此 migration 时（首次失败或手工 repair）不会再次冲突。
-- ============================================================

-- 1) 兼容旧版本（V20261008_02 之前版本）：删除旧唯一索引（如果存在）
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_shipping_rate'
      AND index_name = 'uk_zone_method_active'
);
SET @sql := IF(@idx_exists > 0,
    'ALTER TABLE `mo_shipping_rate` DROP INDEX `uk_zone_method_active`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 加虚拟列：仅当 status='ACTIVE' 时为 1，其余 NULL（不参与唯一约束）
--    如果列已存在则跳过
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_shipping_rate'
      AND column_name = 'active_flag'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_shipping_rate` ADD COLUMN `active_flag` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = ''ACTIVE'' THEN 1 ELSE NULL END) VIRTUAL COMMENT ''ACTIVE=1 / INACTIVE=NULL；NULL 不参与唯一约束，使 INACTIVE 可有多条历史'' AFTER `status`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 新唯一索引：只对 (zone, method, ACTIVE) 唯一
--    如果索引已存在则跳过
SET @new_idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'mo_shipping_rate'
      AND index_name = 'uk_zone_method_active_flag'
);
SET @sql := IF(@new_idx_exists = 0,
    'ALTER TABLE `mo_shipping_rate` ADD UNIQUE KEY `uk_zone_method_active_flag` (`zone_id`, `method_id`, `active_flag`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
