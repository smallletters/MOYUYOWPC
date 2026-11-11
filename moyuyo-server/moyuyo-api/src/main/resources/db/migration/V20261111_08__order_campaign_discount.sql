-- 订单整单活动优惠（满减）
-- 原有 campaign_discount 存的是「活动价优惠 + 整单满减」总额，
-- 其中活动价优惠已体现在订单项价格（order_item.price）里，不能再次从实付中扣减。
-- 订单详情需要单独展示可参与金额计算的整单满减，故新增该字段。
ALTER TABLE mo_order
  ADD COLUMN order_campaign_discount DECIMAL(10,2) NOT NULL DEFAULT 0
  COMMENT '整单活动优惠（满减），不含已体现在商品价中的活动价优惠';
