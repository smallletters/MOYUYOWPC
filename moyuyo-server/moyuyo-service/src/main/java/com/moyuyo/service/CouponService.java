package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /**
     * 可领取优惠券分页列表。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @param userId 当前登录用户 ID（可为 null，表示未登录；此时不会填充 claimedByMe）
     */
    Page<CouponEntity> listAvailable(int page, int size, Long userId);

    CouponEntity getCouponDetail(Long id);

    void claimCoupon(Long userId, Long couponId);

    List<CouponEntity> listUserCoupons(Long userId, String status);

    void useCoupon(Long userId, Long userCouponId, Long orderId);

    /**
     * 订单取消时返还优惠券：仅当该券确为指定订单核销(USED & usedOrderId=orderId)时置回 UNUSED。
     * userCouponId 为空直接忽略。
     */
    void releaseCoupon(Long userCouponId, Long orderId);

    /**
     * 服务端按用户优惠券记录重算下单优惠金额（防止前端伪造减免）。
     *
     * @param userCouponId 用户优惠券记录 id；为 null 表示不使用优惠券，返回 0
     * @param goodsAmount  商品小计（不含运费）
     * @return 本次应减免金额（已封顶 ≤ goodsAmount）；记录不存在/非本人/不可用/未达门槛时抛异常
     */
    BigDecimal computeCouponDiscount(Long userId, Long userCouponId, BigDecimal goodsAmount);

    /** 转赠：将自己的 user_coupon 转移给目标用户 */
    void transferCoupon(Long fromUserId, Long userCouponId, Long toUserId);
}
