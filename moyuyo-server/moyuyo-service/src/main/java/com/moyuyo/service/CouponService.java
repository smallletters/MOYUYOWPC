package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;

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

    /** 转赠：将自己的 user_coupon 转移给目标用户 */
    void transferCoupon(Long fromUserId, Long userCouponId, Long toUserId);
}
