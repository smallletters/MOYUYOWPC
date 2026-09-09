-- 优惠券核销闭环：订单记录使用的用户优惠券记录 ID
-- 下单时服务端即核销(mo_user_coupon.status=USED)，未支付取消订单时返还；据此可对账/防重复使用
ALTER TABLE mo_order
    ADD COLUMN user_coupon_id BIGINT NULL DEFAULT NULL COMMENT '下单使用的用户优惠券记录ID(mo_user_coupon.id)' AFTER coupon_id;

CREATE INDEX idx_order_user_coupon ON mo_order (user_coupon_id);
