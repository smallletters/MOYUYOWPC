-- 订单监控规则引擎增强
-- 1) 规则版本：规则条件（场景/动作/阈值）变更后 version+1，使新条件能重新评估历史订单，
--    同时保留旧版本的执行记录用于审计（唯一键改为 rule_id + rule_version + order_id）
-- 2) 执行失败重试：retry_count 记录失败次数，达到上限后不再重试
-- 3) mo_logistics.last_track_time：物流轨迹最后更新时间，供"物流停滞"规则精确判断
SET @db = (SELECT DATABASE());

-- ==================== mo_order_monitor_rule.version ====================
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_rule' AND COLUMN_NAME = 'version');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_rule ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''规则条件版本，条件变更时 +1'' AFTER priority', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== mo_order_monitor_log.rule_version / retry_count ====================
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_log' AND COLUMN_NAME = 'rule_version');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_log ADD COLUMN rule_version INT NOT NULL DEFAULT 0 COMMENT ''执行时的规则版本'' AFTER rule_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_log' AND COLUMN_NAME = 'retry_count');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_log ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''失败重试次数'' AFTER result', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 唯一键改为 (rule_id, rule_version, order_id)：同一规则同一版本对同一订单只执行一次
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_log' AND INDEX_NAME = 'uk_rule_order');
SET @sql = IF(@exists > 0, 'ALTER TABLE mo_order_monitor_log DROP INDEX uk_rule_order', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_log' AND INDEX_NAME = 'uk_rule_version_order');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_log ADD UNIQUE KEY uk_rule_version_order (rule_id, rule_version, order_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== mo_logistics.last_track_time ====================
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_logistics' AND COLUMN_NAME = 'last_track_time');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_logistics ADD COLUMN last_track_time DATETIME NULL COMMENT ''轨迹最后更新时间'' AFTER traces', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据兜底：没有轨迹更新时间的，用发货时间近似
UPDATE `mo_logistics` SET `last_track_time` = `shipped_at`
WHERE `last_track_time` IS NULL AND `shipped_at` IS NOT NULL;
