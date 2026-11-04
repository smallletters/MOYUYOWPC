-- 营销活动创建表单字段落库：适用商品范围、优惠规则、审批信息
ALTER TABLE `mo_marketing_campaign`
    ADD COLUMN `product_scope` VARCHAR(32) DEFAULT '全部商品' COMMENT '适用商品范围：全部商品/指定品类/指定商品',
    ADD COLUMN `product_categories` VARCHAR(500) DEFAULT NULL COMMENT '适用品类（逗号分隔）',
    ADD COLUMN `discount_type` VARCHAR(32) DEFAULT NULL COMMENT '折扣类型：直降/百分比折扣/满减',
    ADD COLUMN `discount_value` VARCHAR(64) DEFAULT NULL COMMENT '折扣值',
    ADD COLUMN `per_limit` INT DEFAULT 0 COMMENT '每人限用次数',
    ADD COLUMN `stock_limit` TINYINT(1) DEFAULT 1 COMMENT '是否启用库存限制',
    ADD COLUMN `total_count` INT DEFAULT 0 COMMENT '发放总量',
    ADD COLUMN `approver` VARCHAR(64) DEFAULT NULL COMMENT '审批人',
    ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT '审批备注';
