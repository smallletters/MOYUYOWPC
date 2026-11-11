-- 积分商城商品改为复用主商品表：给 mo_product 增加积分兑换配置
-- 说明：allow_points_exchange=1 且 points_price>0 且上架的商品，才会出现在积分商城
ALTER TABLE mo_product
  ADD COLUMN allow_points_exchange TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持积分兑换：0否 1是',
  ADD COLUMN points_price INT NOT NULL DEFAULT 0 COMMENT '兑换所需积分，0 表示未配置',
  ADD COLUMN points_stock INT NOT NULL DEFAULT 0 COMMENT '积分兑换专属库存，-1 表示不限';

-- 积分商城列表按「可兑换 + 上架」过滤，建立联合索引避免全表扫描
CREATE INDEX idx_points_exchange ON mo_product (allow_points_exchange, on_sale);

-- 兑换记录关联生成的 0 元积分订单，便于后台按订单发货、按兑换单追溯
ALTER TABLE mo_points_exchange
  ADD COLUMN product_id BIGINT NULL COMMENT '对应主商品 ID',
  ADD COLUMN order_id BIGINT NULL COMMENT '生成的 0 元积分订单 ID';

-- 积分兑换订单按商品维度成交、不绑定 SKU，需要放开订单明细的 sku_id 非空约束
ALTER TABLE mo_order_item
  MODIFY COLUMN sku_id BIGINT NULL COMMENT 'SKU ID（无 SKU 商品/积分兑换订单为空）';

-- 启用方式：后台「商品编辑 → 高级 → 积分兑换」勾选并填写积分价/库存，
-- 或直接执行下面语句（示例：把商品 1 设为 500 积分可兑换、限量 100 件）：
-- UPDATE mo_product SET allow_points_exchange = 1, points_price = 500, points_stock = 100 WHERE id = 1;

