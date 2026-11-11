-- 订单侧预售支持
-- 1) order_type：区分普通单 / 预售单
-- 2) presale_id：关联 mo_product_presale.id，便于统计与限购校验
-- 3) presale_stage：预售阶段 FULL 全款 / DEPOSIT 定金 / BALANCE 尾款
-- 4) parent_order_id：尾款单关联的定金单 ID（普通单为空）
--
-- 说明：mo_order 在生产环境按月分区，唯一索引必须包含分区键 create_time，
-- 因此 parent_order_id 只建普通索引，尾款单幂等由应用层事务内校验保证。
SET @db = (SELECT DATABASE());

-- order_type：订单类型（历史订单默认 NORMAL）
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'order_type');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT ''NORMAL'' COMMENT ''订单类型: NORMAL 普通 / PRESALE 预售'' AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- presale_id：预售配置 ID
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'presale_id');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN presale_id BIGINT NULL COMMENT ''预售配置ID（mo_product_presale.id）'' AFTER order_type', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- presale_stage：预售阶段
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'presale_stage');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN presale_stage VARCHAR(16) NULL COMMENT ''预售阶段: FULL 全款 / DEPOSIT 定金 / BALANCE 尾款'' AFTER presale_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- parent_order_id：尾款单关联的定金单 ID
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'parent_order_id');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN parent_order_id BIGINT NULL COMMENT ''父订单ID（尾款单指向定金单）'' AFTER presale_stage', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 索引：按预售配置查询订单（统计/限购）、按定金单查询尾款单
SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND INDEX_NAME = 'idx_order_presale');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD INDEX idx_order_presale (presale_id, user_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND INDEX_NAME = 'idx_order_parent');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD INDEX idx_order_parent (parent_order_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
