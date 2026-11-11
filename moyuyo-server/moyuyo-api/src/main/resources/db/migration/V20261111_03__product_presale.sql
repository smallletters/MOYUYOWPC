-- 商品预售配置（1:1 商品，独立表，不写入 mo_product.attributes）
-- 支持三种预售形态：
--   DISPLAY 展示型预售（只展示预售时间/发货时间，下单仍按现货价）
--   FULL    全款预售（预售期内一次性支付 presale_price）
--   DEPOSIT 定金预售（先付 deposit_amount，尾款期支付 presale_price - deposit_offset）
CREATE TABLE IF NOT EXISTS `mo_product_presale` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID（1:1）',
  `presale_type` VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT '预售类型: DISPLAY/FULL/DEPOSIT',
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '预售状态: DRAFT/SCHEDULED/ONGOING/SOLD_OUT/ENDED',
  `presale_price` DECIMAL(10,2) DEFAULT NULL COMMENT '预售价格（不得高于商品售价）',
  `start_time` DATETIME DEFAULT NULL COMMENT '预售开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '预售结束时间',
  `ship_date` DATE DEFAULT NULL COMMENT '预计发货日期',
  `stock_limit` INT DEFAULT NULL COMMENT '预售库存上限（NULL 表示不限）',
  `sold_count` INT NOT NULL DEFAULT 0 COMMENT '已售预售数量',
  `limit_per_user` INT DEFAULT NULL COMMENT '每人限购数量（NULL 表示不限）',
  `auto_cancel_minutes` INT DEFAULT NULL COMMENT '未付款自动取消分钟数（NULL 表示用系统默认）',
  `deposit_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '定金金额（DEPOSIT 类型必填）',
  `deposit_offset` DECIMAL(10,2) DEFAULT NULL COMMENT '定金抵扣金额（尾款 = 预售价 - 抵扣金额）',
  `balance_start` DATETIME DEFAULT NULL COMMENT '尾款支付开始时间',
  `balance_end` DATETIME DEFAULT NULL COMMENT '尾款支付结束时间',
  `deposit_refundable` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '定金是否可退: 1可退 0不可退',
  `notice_text` VARCHAR(500) DEFAULT NULL COMMENT '预售须知（APP 详情页展示）',
  `compensation_days` INT DEFAULT NULL COMMENT '延迟发货补偿：逾期天数阈值',
  `compensation_percent` DECIMAL(5,2) DEFAULT NULL COMMENT '延迟发货补偿：补偿比例（百分比）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`),
  KEY `idx_status_start` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品预售配置';
