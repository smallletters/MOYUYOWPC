-- 订单监控规则引擎
-- 1) 规则表补充结构化条件字段（scene + 阈值），供定时任务真正执行
-- 2) 新增执行记录表 mo_order_monitor_log，唯一键保证同一规则对同一订单只执行一次
SET @db = (SELECT DATABASE());

-- scene：触发场景 TIMEOUT_PAY / TIMEOUT_SHIP / LOGISTICS_STALL / AMOUNT_DEVIATION
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_rule' AND COLUMN_NAME = 'scene');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_rule ADD COLUMN scene VARCHAR(32) NULL COMMENT ''触发场景: TIMEOUT_PAY/TIMEOUT_SHIP/LOGISTICS_STALL/AMOUNT_DEVIATION'' AFTER name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- threshold_hours：超时类场景的阈值（小时）
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_rule' AND COLUMN_NAME = 'threshold_hours');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_rule ADD COLUMN threshold_hours INT NULL COMMENT ''超时阈值（小时）'' AFTER scene', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- threshold_percent：金额偏差场景的阈值（百分比）
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order_monitor_rule' AND COLUMN_NAME = 'threshold_percent');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order_monitor_rule ADD COLUMN threshold_percent INT NULL COMMENT ''金额偏差阈值（百分比）'' AFTER threshold_hours', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史规则回填结构化条件（按规则名称关键词识别，兼容不同环境自增 ID 不一致）
UPDATE `mo_order_monitor_rule`
SET `scene` = 'TIMEOUT_PAY', `threshold_hours` = 24
WHERE `scene` IS NULL AND (`name` LIKE '%未付款%' OR `condition_text` LIKE '%未付款%');

UPDATE `mo_order_monitor_rule`
SET `scene` = 'TIMEOUT_SHIP', `threshold_hours` = 48
WHERE `scene` IS NULL AND (`name` LIKE '%未发货%' OR `condition_text` LIKE '%未发货%');

UPDATE `mo_order_monitor_rule`
SET `scene` = 'LOGISTICS_STALL', `threshold_hours` = 72
WHERE `scene` IS NULL AND (`name` LIKE '%物流%' OR `condition_text` LIKE '%物流%');

UPDATE `mo_order_monitor_rule`
SET `scene` = 'AMOUNT_DEVIATION', `threshold_percent` = 50
WHERE `scene` IS NULL AND (`name` LIKE '%金额%' OR `condition_text` LIKE '%金额%');

-- 执行记录表：用于幂等（同一规则 + 同一订单只处理一次）与执行审计
CREATE TABLE IF NOT EXISTS `mo_order_monitor_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(32) DEFAULT NULL COMMENT '订单号（冗余，便于排查）',
  `scene` VARCHAR(32) DEFAULT NULL COMMENT '触发场景',
  `action_type` VARCHAR(16) NOT NULL COMMENT '执行动作: FLAG/INTERCEPT/CANCEL',
  `result` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '执行结果: PENDING/SUCCESS/FAILED',
  `message` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_order` (`rule_id`, `order_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单监控规则执行记录';
