-- 扩展风控告警配置的规则类型列宽度
-- 原因：常见规则类型名（如 order_failure_rate、new_account_first_order_limit 等）
-- 超过 varchar(16)，插入时触发 Data too long → 409，导致新建告警配置失败
ALTER TABLE `mo_risk_alert_config`
  MODIFY COLUMN `alert_type` varchar(64) NOT NULL COMMENT '告警规则类型';
