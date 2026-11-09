-- ============================================================
-- V20261109_01__extend_after_sales.sql
-- 售后工单增强：补齐退货退款/仅退款/换货闭环所需字段
-- 状态机：PENDING(待审核) → APPROVED(待寄回) → RETURNING(已寄回) → RECEIVED(已收货) → COMPLETED(已完成)
--         PENDING → REJECTED(已拒绝)；PENDING/APPROVED → CANCELLED(已取消)
-- ============================================================

ALTER TABLE `mo_after_sales`
    ADD COLUMN `after_sale_no` VARCHAR(40) NULL COMMENT '售后单号',
    ADD COLUMN `sku_id` BIGINT NULL COMMENT 'SKU ID',
    ADD COLUMN `product_id` BIGINT NULL COMMENT '商品 ID',
    ADD COLUMN `product_name` VARCHAR(255) NULL COMMENT '商品名称快照',
    ADD COLUMN `sku_spec` VARCHAR(255) NULL COMMENT '规格快照',
    ADD COLUMN `main_image` VARCHAR(500) NULL COMMENT '商品主图快照',
    ADD COLUMN `quantity` INT NOT NULL DEFAULT 1 COMMENT '售后数量',
    ADD COLUMN `delivery_type` VARCHAR(20) NULL COMMENT '退货方式 RETURN_SHIP(寄回)/NO_RETURN(无需寄回)',
    ADD COLUMN `return_company` VARCHAR(100) NULL COMMENT '退货物流公司',
    ADD COLUMN `return_tracking_no` VARCHAR(100) NULL COMMENT '退货运单号',
    ADD COLUMN `return_time` DATETIME NULL COMMENT '用户寄回时间',
    ADD COLUMN `receive_time` DATETIME NULL COMMENT '商家收货时间',
    ADD COLUMN `refund_id` BIGINT NULL COMMENT '关联退款单 ID',
    ADD COLUMN `refund_no` VARCHAR(40) NULL COMMENT '关联退款单号',
    ADD COLUMN `reject_reason` VARCHAR(500) NULL COMMENT '拒绝原因',
    ADD COLUMN `reject_time` DATETIME NULL COMMENT '拒绝时间',
    ADD COLUMN `complete_time` DATETIME NULL COMMENT '完成时间',
    ADD COLUMN `operator_id` BIGINT NULL COMMENT '最近处理人 ID',
    ADD COLUMN `remark` VARCHAR(500) NULL COMMENT '商家备注/换货物流',
    MODIFY COLUMN `status` VARCHAR(20) DEFAULT 'PENDING'
        COMMENT 'PENDING/APPROVED/RETURNING/RECEIVED/COMPLETED/REJECTED/CANCELLED',
    ADD INDEX `idx_after_sale_no` (`after_sale_no`),
    ADD INDEX `idx_order_item_status` (`order_item_id`, `status`),
    ADD INDEX `idx_after_sales_status_time` (`status`, `create_time`);
