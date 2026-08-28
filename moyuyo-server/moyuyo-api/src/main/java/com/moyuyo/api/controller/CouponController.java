package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.UserCouponMapper;
import com.moyuyo.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "优惠券管理")
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final UserCouponMapper userCouponMapper;

    @Operation(summary = "可领取优惠券列表")
    @GetMapping
    public Result<Page<CouponEntity>> listAvailable(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 分页参数统一守卫
        int[] pageParams = PageParamGuard.normalize(page, size, 20);
        // 已登录用户带上 userId，用于返回 claimedByMe 标记前端判断按钮态
        Long userId = UserContextHolder.getUserId();
        return Result.success(couponService.listAvailable(pageParams[0], pageParams[1], userId));
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{id}/claim")
    public Result<Void> claimCoupon(@PathVariable Long id) {
        couponService.claimCoupon(UserContextHolder.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "我的优惠券")
    @GetMapping("/mine")
    public Result<List<CouponEntity>> myCoupons(
            @RequestParam(required = false) String status) {
        return Result.success(couponService.listUserCoupons(UserContextHolder.getUserId(), status));
    }

    @Operation(summary = "使用优惠券")
    @PostMapping("/{userCouponId}/use")
    public Result<Void> useCoupon(
            @PathVariable Long userCouponId,
            @RequestParam Long orderId) {
        couponService.useCoupon(UserContextHolder.getUserId(), userCouponId, orderId);
        return Result.success();
    }

    @Operation(summary = "优惠券详情")
    @GetMapping("/{id}")
    public Result<CouponEntity> getDetail(@PathVariable Long id) {
        return Result.success(couponService.getCouponDetail(id));
    }

    @Operation(summary = "我的优惠券详情")
    @GetMapping("/user-coupon/{userCouponId}")
    public Result<java.util.Map<String, Object>> userCouponDetail(@PathVariable Long userCouponId) {
        Long userId = UserContextHolder.getUserId();
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("用户优惠券不存在");
        }
        CouponEntity c = couponService.getCouponDetail(uc.getCouponId());
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("id", uc.getId());
        r.put("couponId", uc.getCouponId());
        r.put("status", uc.getStatus());
        r.put("usedTime", uc.getUsedTime());
        r.put("usedOrderId", uc.getUsedOrderId());
        r.put("createTime", uc.getCreateTime());
        if (c != null) {
            r.put("name", c.getName());
            r.put("description", c.getDescription());
            r.put("type", c.getType());
            r.put("discountValue", c.getDiscountValue());
            r.put("minOrderAmount", c.getMinOrderAmount());
            r.put("maxDiscountAmount", c.getMaxDiscountAmount());
        }
        return Result.success(r);
    }

    @Operation(summary = "转赠优惠券")
    @PostMapping("/{userCouponId}/transfer")
    public Result<Void> transfer(
            @PathVariable Long userCouponId,
            @RequestParam Long toUserId) {
        couponService.transferCoupon(UserContextHolder.getUserId(), userCouponId, toUserId);
        return Result.success();
    }
}
