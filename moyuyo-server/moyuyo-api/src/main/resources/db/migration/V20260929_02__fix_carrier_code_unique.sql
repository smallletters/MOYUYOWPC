-- ============================================================
-- V20260929_02__fix_carrier_code_unique.sql
-- 修复 V20260929_01 在生产环境首次部署失败的问题
--
-- 失败原因：
--   V20260929_01 末尾的 `ALTER TABLE mo_carrier ADD UNIQUE INDEX uk_carrier_code (code)`
--   在生产环境会失败：
--     1) 历史承运商存在 code IS NULL 行（MySQL UNIQUE 允许多个 NULL，但业务侧需要唯一）
--     2) 即使 UPDATE 补齐 8 条主流承运商，仍可能存在 code='' 空字符串重复
--     3) uk_carrier_code 索引可能已部分加成功（执行断点）
--
-- 修复要点（可重复幂等执行）：
--   1) 把 code='' 或 code IS NULL 的行填一个唯一占位 'legacy_<id>'
--   2) 把当前已存在的非空 code 也补一遍（防御：万一有人手工塞了空字符串）
--   3) 幂等 ADD UNIQUE INDEX（先查 information_schema 再决定）
-- ============================================================

-- 1) 空值/重复值 dedup：把 code='' 或 NULL 的全部填上 'legacy_<id>'
--    避免后续唯一索引冲突；同时对所有历史承运商做兜底 UPDATE（按 name 匹配主流承运商）
UPDATE `mo_carrier` SET `code` = CONCAT('legacy_', `id`)
WHERE `code` IS NULL OR `code` = '';

UPDATE `mo_carrier` SET `code` = 'sf'        WHERE `name` = '顺丰速运'  AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'zto'       WHERE `name` = '中通快递'  AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'yto'       WHERE `name` = '圆通速递'  AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'yd'        WHERE `name` = '韵达快递'  AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'jtexpress' WHERE `name` = '极兔速递'  AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'usps'      WHERE `name` = 'USPS'      AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'fedex'     WHERE `name` = 'FedEx'     AND `code` LIKE 'legacy_%';
UPDATE `mo_carrier` SET `code` = 'dhl'       WHERE `name` = 'DHL'       AND `code` LIKE 'legacy_%';

-- 2) 燕文种子：使用 INSERT IGNORE 避免重复运行时报主键冲突
INSERT IGNORE INTO `mo_carrier`
  (`id`, `name`, `code`, `transport_mode`, `avg_delivery_days`,
   `first_weight_price`, `renew_weight_price`, `praise_rate`,
   `label_api_enabled`, `api_remark`, `status`, `create_time`)
VALUES
(195000009, '燕文物流', 'yanwen', 'MIX', 7.0, 35.0, 22.0, 95.0, 1,
 '跨境电商常用承运商；支持电子面单 API（express.order.label.get），需在燕文客户中心开通账号并配置 userId/apiToken/channelId',
 'ACTIVE', NOW());

-- 3) 幂等 ADD UNIQUE INDEX：先查 information_schema，再决定是否执行
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'mo_carrier'
      AND INDEX_NAME   = 'uk_carrier_code'
);

SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `mo_carrier` ADD UNIQUE INDEX `uk_carrier_code` (`code`)',
    'SELECT ''uk_carrier_code already exists, skip'' AS msg'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;