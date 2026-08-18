-- 分步添加缺失列，使用信息模式检查避免重复
SET @db = (SELECT DATABASE());

-- cancel_time 已存在则跳过
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'cancel_time');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN cancel_time DATETIME NULL COMMENT ''取消时间'' AFTER paid_at', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- cancel_reason
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'cancel_reason');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN cancel_reason VARCHAR(255) NULL COMMENT ''取消原因'' AFTER cancel_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- deliver_time
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'deliver_time');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN deliver_time DATETIME NULL COMMENT ''发货时间'' AFTER cancel_reason', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sender_name
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'sender_name');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN sender_name VARCHAR(64) NULL COMMENT ''发件人姓名'' AFTER remark', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sender_phone
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'sender_phone');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN sender_phone VARCHAR(32) NULL COMMENT ''发件人电话'' AFTER sender_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sender_address
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'sender_address');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN sender_address VARCHAR(512) NULL COMMENT ''发件人地址'' AFTER sender_phone', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- receiver_name
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'receiver_name');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN receiver_name VARCHAR(64) NULL COMMENT ''收件人姓名'' AFTER sender_address', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- receiver_phone
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'receiver_phone');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN receiver_phone VARCHAR(32) NULL COMMENT ''收件人电话'' AFTER receiver_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- receiver_address
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'receiver_address');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN receiver_address VARCHAR(512) NULL COMMENT ''收件人地址'' AFTER receiver_phone', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- receiver_zip
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'receiver_zip');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN receiver_zip VARCHAR(16) NULL COMMENT ''收件人邮编'' AFTER receiver_address', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- shipping_method
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'shipping_method');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN shipping_method VARCHAR(32) NULL COMMENT ''配送方式'' AFTER receiver_zip', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- delivery_note
SET @exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mo_order' AND COLUMN_NAME = 'delivery_note');
SET @sql = IF(@exists = 0, 'ALTER TABLE mo_order ADD COLUMN delivery_note VARCHAR(255) NULL COMMENT ''发货备注'' AFTER shipping_method', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
