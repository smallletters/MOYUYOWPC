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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    public Result<Page<CouponView>> listAvailable(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 分页参数统一守卫
        int[] pageParams = PageParamGuard.normalize(page, size, 20);
        // 已登录用户带上 userId，用于返回 claimedByMe 标记前端判断按钮态
        Long userId = UserContextHolder.getUserId();
        Page<CouponEntity> raw = couponService.listAvailable(pageParams[0], pageParams[1], userId);
        // 列表里的 CouponEntity 也需要暴露 displayValue(折数 / 金额)给前端做卡片展示
        Page<CouponView> viewPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        viewPage.setRecords(raw.getRecords().stream().map(CouponView::from).collect(java.util.stream.Collectors.toList()));
        return Result.success(viewPage);
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/{id}/claim")
    public Result<Void> claimCoupon(@PathVariable Long id) {
        couponService.claimCoupon(UserContextHolder.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "我的优惠券")
    @GetMapping("/mine")
    public Result<List<CouponView>> myCoupons(
            @RequestParam(required = false) String status) {
        // CouponEntity 中的 discountValue 是『价格百分比』(PERCENT) / 减免金额(AMOUNT),
        // 通过 CouponView 额外暴露 displayValue：『折数』(PERCENT) / 减免金额(AMOUNT),
        // 避免 C 端按字面值展示金额时把"9 折"显示为"90 元"。
        // 另外必须暴露 userCouponId(mo_user_coupon.id)：前端下单要拿它核销券，
        // 原实现只返回券模板，导致 checkout 提交的 couponUserId 永远为 undefined（P0 资金口径缺陷）。
        List<UserCouponEntity> list = couponService.listUserCoupons(UserContextHolder.getUserId(), status);
        List<CouponView> views = new ArrayList<>(list.size());
        if (!list.isEmpty()) {
            // 批量查券模板，避免 N+1
            List<Long> couponIds = list.stream()
                    .map(UserCouponEntity::getCouponId)
                    .collect(java.util.stream.Collectors.toList());
            java.util.Map<Long, CouponEntity> couponMap = couponService.mapCouponsByIds(couponIds);
            for (UserCouponEntity uc : list) {
                // 保持"一条 user_coupon 一条记录"语义：以 uc 为主键组装视图，模板字段从 couponMap 取
                CouponView v = CouponView.from(couponMap.get(uc.getCouponId()), uc);
                if (v != null) views.add(v);
            }
        }
        return Result.success(views);
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
    public Result<CouponView> getDetail(@PathVariable Long id) {
        CouponEntity c = couponService.getCouponDetail(id);
        return Result.success(c == null ? null : CouponView.from(c));
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
            // 额外暴露 displayValue：PERCENT 时为『折数』，与 C 端展示口径一致
            r.put("displayValue", toDisplayValue(c));
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

    /** PERCENT 类型将『价格百分比』(90)转回『折数』(9);其它类型原样返回 */
    private static BigDecimal toDisplayValue(CouponEntity c) {
        if (c == null || c.getDiscountValue() == null) return null;
        if ("PERCENT".equalsIgnoreCase(c.getType())) {
            // 保留 1 位小数,不做 stripTrailingZeros 避免 BigDecimal 转字符串出现科学计数法
            return c.getDiscountValue().divide(new BigDecimal(10), 1, RoundingMode.HALF_UP);
        }
        return c.getDiscountValue();
    }

    /**
     * C 端优惠券展示视图：在 CouponEntity 基础上额外暴露 displayValue，
     * 使前端拿到与 UI 语义一致的字段值（PERCENT → 折数；AMOUNT → 金额）。
     * <p>
     * 另携带用户券维度字段（仅 /mine 接口填充）：
     * <ul>
     *   <li>{@code userCouponId} = mo_user_coupon.id（前端下单核销必须用它，而非券模板 id）</li>
     *   <li>{@code couponId}     = 券模板 id（等价于 id，命名对齐前端语义）</li>
     *   <li>{@code status}       = 用户券状态 UNUSED / LOCKED / USED</li>
     * </ul>
     * 注意：{@code id} 仍为券模板 id，不能改（前端 bestCoupon / isCouponAvailable 依赖该字段名）。
     */
    public static class CouponView {
        public Long id;
        public String name;
        public String description;
        public String type;
        public BigDecimal discountValue;
        public BigDecimal displayValue;
        public BigDecimal minOrderAmount;
        public BigDecimal maxDiscountAmount;
        public Integer totalCount;
        public Integer claimedCount;
        public Integer usedCount;
        public java.time.LocalDateTime startTime;
        public java.time.LocalDateTime endTime;
        public Boolean active;
        public Boolean claimedByMe;
        public java.time.LocalDateTime createTime;

        // ===== 用户券维度（仅"我的优惠券"填充；可领列表/详情为 null）=====
        /** 用户优惠券记录 id（mo_user_coupon.id），下单核销键 */
        public Long userCouponId;
        /** 券模板 id（与 id 等值，语义更明确） */
        public Long couponId;
        /** 用户券状态：UNUSED / LOCKED（下单占用）/ USED */
        public String status;

        public static CouponView from(CouponEntity c) {
            if (c == null) return null;
            CouponView v = new CouponView();
            v.id = c.getId();
            v.couponId = c.getId();
            v.name = c.getName();
            v.description = c.getDescription();
            v.type = c.getType();
            v.discountValue = c.getDiscountValue();
            v.displayValue = toDisplayValue(c);
            v.minOrderAmount = c.getMinOrderAmount();
            v.maxDiscountAmount = c.getMaxDiscountAmount();
            v.totalCount = c.getTotalCount();
            v.claimedCount = c.getClaimedCount();
            v.usedCount = c.getUsedCount();
            v.startTime = c.getStartTime();
            v.endTime = c.getEndTime();
            v.active = c.getActive();
            v.claimedByMe = c.getClaimedByMe();
            v.createTime = c.getCreateTime();
            return v;
        }

        /**
         * 组装"我的优惠券"视图：模板字段取 coupon，用户券字段取 uc。
         * 模板不存在（券被下架/删除）时返回 null，由调用方过滤。
         */
        public static CouponView from(CouponEntity c, UserCouponEntity uc) {
            CouponView v = from(c);
            if (v == null) return null;
            if (uc != null) {
                v.userCouponId = uc.getId();
                v.couponId = uc.getCouponId();
                v.status = uc.getStatus();
            }
            return v;
        }
    }
}
