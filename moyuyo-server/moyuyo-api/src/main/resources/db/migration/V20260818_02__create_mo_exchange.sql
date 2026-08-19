-- 换货单表：用户申请换货时生成一条记录，与 mo_refund 共存
-- 换货流程：APPLIED → APPROVED → SHIPPED_BACK(用户寄回) → RESHIPPED(新货发出) → COMPLETED
-- 任意阶段可 CANCELLED
CREATE TABLE IF NOT EXISTS `mo_exchange` (
  `id`              BIGINT         NOT NULL,
  `order_id`        BIGINT         NOT NULL                COMMENT '原订单ID',
  `exchange_no`     VARCHAR(32)    NOT NULL                COMMENT '换货单号',
  `old_sku_id`      BIGINT         NOT NULL                COMMENT '原 SKU ID',
  `old_quantity`    INT            NOT NULL                COMMENT '原商品数量',
  `new_sku_id`      BIGINT         NOT NULL                COMMENT '换新 SKU ID',
  `new_quantity`    INT            NOT NULL                COMMENT '换新商品数量',
  `reason`          VARCHAR(64)    NULL                    COMMENT '换货原因(SIZE_WRONG/QUALITY_ISSUE/OTHER)',
  `description`     TEXT           NULL                    COMMENT '问题描述',
  `images`          JSON           NULL                    COMMENT '举证图片(JSON 数组)',
  `status`          VARCHAR(16)    NOT NULL                COMMENT 'APPLIED/APPROVED/SHIPPED_BACK/RESHIPPED/COMPLETED/CANCELLED',
  `carrier`         VARCHAR(32)    NULL                    COMMENT '用户回寄承运商',
  `tracking_no`     VARCHAR(64)    NULL                    COMMENT '用户回寄物流单号',
  `reship_carrier`  VARCHAR(32)    NULL                    COMMENT '新货发出承运商',
  `reship_tracking` VARCHAR(64)    NULL                    COMMENT '新货发出物流单号',
  `apply_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approve_time`    DATETIME       NULL,
  `complete_time`   DATETIME       NULL,
  `cancel_time`     DATETIME       NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_no` (`exchange_no`),
  KEY `idx_exchange_order` (`order_id`),
  KEY `idx_exchange_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '换货单表';
