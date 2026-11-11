-- 订阅订单支持：记录订单对应的订阅方案 ID（order_type = SUBSCRIPTION）
-- 说明：mo_order 生产环境按月分区，唯一索引必须包含分区键 create_time，
-- 本次仅新增普通列，不改动已有索引/分区。
SET @db = (SELECT DATABASE());

SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'subscribe_plan_id');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN subscribe_plan_id BIGINT NULL COMMENT ''订阅方案ID（order_type=SUBSCRIPTION 时）'' AFTER presale_stage', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
