-- 商品级积分抵扣开关：关闭后 APP 结算页不再展示该商品的积分抵扣（整单存在关闭商品时隐藏）
-- 默认 1（允许），保持存量商品行为不变
ALTER TABLE mo_product
  ADD COLUMN allow_points_deduct TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许积分抵扣：0否 1是';
