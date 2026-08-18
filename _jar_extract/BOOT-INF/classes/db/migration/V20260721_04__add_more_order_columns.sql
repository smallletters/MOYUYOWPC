-- 补充 mo_order 表剩余缺失列
SET @db = (SELECT DATABASE());

-- received_time
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'received_time');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN received_time DATETIME NULL COMMENT ''收货时间'' AFTER deliver_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- update_time（必须先于 delete_status，因为 delete_status 引用 AFTER update_time）
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'update_time');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN update_time DATETIME NULL COMMENT ''更新时间'' AFTER received_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- delete_status
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'delete_status');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN delete_status TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''删除状态 0正常 1已删'' AFTER update_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
