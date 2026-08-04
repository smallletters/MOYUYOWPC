-- 订单监控规则表
-- 用于支持 AdminOrderOpsController 中 /monitor/rules CRUD 接口
-- 表名约定：mo_ 前缀，与现有 Flyway 迁移保持一致
CREATE TABLE IF NOT EXISTS `mo_order_monitor_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
  `condition_text` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '触发条件描述',
  `action_type` VARCHAR(50) NOT NULL DEFAULT 'FLAG' COMMENT '自动动作: FLAG(标记)/INTERCEPT(拦截)/CANCEL(取消)',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
  `priority` INT NOT NULL DEFAULT 100 COMMENT '优先级，数字越小优先级越高',
  `creator` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单监控自动处理规则';

-- 示例数据：与前端 OrderMonitor.vue 初始示例保持一致
INSERT IGNORE INTO `mo_order_monitor_rule`
  (`id`, `name`, `condition_text`, `action_type`, `enabled`, `priority`, `creator`)
VALUES
  (1, '超时未付款自动取消', '下单超过 24h 未完成支付，自动取消订单并释放库存', 'CANCEL', 1, 10, '系统'),
  (2, '超时未发货自动提醒', '支付后超过 48h 未发货，自动发送站内信提醒商家', 'FLAG', 1, 20, '系统'),
  (3, '物流停滞自动核查', '物流超过 72h 无更新记录，自动向物流公司发起查询工单', 'FLAG', 1, 30, '系统'),
  (4, '金额异常自动冻结', '实付金额与商品标价偏差超过 50%，自动冻结订单等待审核', 'INTERCEPT', 0, 40, '系统');
