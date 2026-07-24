-- 订单运营相关表
-- 改价记录表
CREATE TABLE IF NOT EXISTS `mo_order_price_modify` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `original_amount` DECIMAL(12,2) NOT NULL COMMENT '原始金额',
  `adjust_amount` DECIMAL(12,2) NOT NULL COMMENT '调整金额(正为加价,负为减价)',
  `final_amount` DECIMAL(12,2) NOT NULL COMMENT '最终金额',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '改价原因',
  `reason_type` VARCHAR(50) DEFAULT NULL COMMENT '改价类型: FREIGHT(补运费)/DISCOUNT(减差价)/MANUAL(人工优惠)',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单改价记录';

-- 订单拦截记录表
CREATE TABLE IF NOT EXISTS `mo_order_intercept` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `intercept_type` VARCHAR(50) NOT NULL COMMENT '拦截类型: RISK(风控)/MANUAL(人工)/SYSTEM(系统)',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '拦截原因',
  `reason_template` VARCHAR(100) DEFAULT NULL COMMENT '拦截原因模板',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RELEASED',
  `release_reason` VARCHAR(500) DEFAULT NULL COMMENT '解除原因',
  `release_operator` VARCHAR(100) DEFAULT NULL COMMENT '解除操作人',
  `release_time` DATETIME DEFAULT NULL COMMENT '解除时间',
  `notify_user` TINYINT DEFAULT 1 COMMENT '是否通知用户: 0否 1是',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单拦截记录';

-- 订单打印记录表
CREATE TABLE IF NOT EXISTS `mo_order_print_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `print_type` VARCHAR(50) NOT NULL COMMENT '打印类型: PICK(拣货单)/PACK(打包单)/SHIP(发货单)/LABEL(配货标签)',
  `template_name` VARCHAR(100) DEFAULT NULL COMMENT '模板名称',
  `paper_size` VARCHAR(20) DEFAULT NULL COMMENT '纸张规格',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `print_count` INT DEFAULT 1 COMMENT '打印次数',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单打印记录';
