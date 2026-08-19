-- 退款单表补 items 字段：支持拆单退款明细（按 SKU 维度记录退款数量与金额）
ALTER TABLE `mo_refund`
  ADD COLUMN `items` JSON NULL COMMENT '拆单退款明细（JSON 数组：skuId/quantity/amount/reason）' AFTER `images`;
