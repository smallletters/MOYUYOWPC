-- ============================================================
-- V20260929_01__add_carrier_api_config.sql
-- 扩展 mo_carrier 表：增加承运商 API 接入配置字段
-- 用途：支持燕文物流、菜鸟、快递100等电子面单对接，
--      用于"订单打印快递单"功能 — 后端可根据 carrier.code
--      路由到对应的 SDK 客户端去调取/打印面单。
-- ============================================================

-- 承运商编码（用于 API 路由）：yanwen / cainiao / kuaidi100 / sf / jd ...
ALTER TABLE `mo_carrier` ADD COLUMN `code` VARCHAR(32) DEFAULT NULL COMMENT '承运商编码：yanwen/cainiao/kuaidi100/sf/jd/yto/...' AFTER `name`;

-- 客户端编号（user_id / partner_id）
ALTER TABLE `mo_carrier` ADD COLUMN `api_user_id` VARCHAR(64) DEFAULT NULL COMMENT 'API 账号/客户号（如燕文 userId）' AFTER `code`;

-- API 密钥/Token（apitoken / partnerKey / appSecret）
ALTER TABLE `mo_carrier` ADD COLUMN `api_token` VARCHAR(255) DEFAULT NULL COMMENT 'API 密钥/Token' AFTER `api_user_id`;

-- 渠道 ID/产品编码（如燕文 channelId、菜鸟 cpCode）
ALTER TABLE `mo_carrier` ADD COLUMN `channel_id` VARCHAR(64) DEFAULT NULL COMMENT '渠道/产品编码（如燕文 channelId）' AFTER `api_token`;

-- API 基础地址（不同承运商接入环境不同，支持自定义）
ALTER TABLE `mo_carrier` ADD COLUMN `api_base_url` VARCHAR(255) DEFAULT NULL COMMENT 'API 基础地址（支持自建测试环境）' AFTER `channel_id`;

-- 是否启用电子面单 API（0=关闭，手动填运单号；1=启用，调用 API 取号打单）
ALTER TABLE `mo_carrier` ADD COLUMN `label_api_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用电子面单 API：0=关闭 1=启用' AFTER `api_base_url`;

-- 备注（服务商对接说明、特殊参数等）
ALTER TABLE `mo_carrier` ADD COLUMN `api_remark` VARCHAR(500) DEFAULT NULL COMMENT 'API 备注/对接说明' AFTER `label_api_enabled`;

-- 给已存在的承运商补充编码，便于前端按编码匹配
UPDATE `mo_carrier` SET `code` = 'sf'        WHERE `name` = '顺丰速运' AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'zto'       WHERE `name` = '中通快递' AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'yto'       WHERE `name` = '圆通速递' AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'yd'        WHERE `name` = '韵达快递' AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'jtexpress' WHERE `name` = '极兔速递' AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'usps'      WHERE `name` = 'USPS'    AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'fedex'     WHERE `name` = 'FedEx'   AND (`code` IS NULL OR `code` = '');
UPDATE `mo_carrier` SET `code` = 'dhl'       WHERE `name` = 'DHL'     AND (`code` IS NULL OR `code` = '');

-- 新增种子：燕文物流（跨境常用，主推对接）
INSERT INTO `mo_carrier` (`id`, `name`, `code`, `transport_mode`, `avg_delivery_days`, `first_weight_price`, `renew_weight_price`, `praise_rate`, `label_api_enabled`, `api_remark`, `status`, `create_time`)
VALUES
(195000009, '燕文物流', 'yanwen', 'MIX', 7.0, 35.0, 22.0, 95.0, 1,
 '跨境电商常用承运商；支持电子面单 API（express.order.label.get），需在燕文客户中心开通账号并配置 userId/apiToken/channelId',
 'ACTIVE', NOW());

-- 唯一索引：防止重复添加相同编码的承运商
ALTER TABLE `mo_carrier` ADD UNIQUE INDEX `uk_carrier_code` (`code`);