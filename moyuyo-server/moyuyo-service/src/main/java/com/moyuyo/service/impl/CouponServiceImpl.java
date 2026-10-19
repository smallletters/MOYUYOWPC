package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.CouponMapper;
import com.moyuyo.dao.mapper.UserCouponMapper;
import com.moyuyo.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    @SuppressWarnings("null")
    public Page<CouponEntity> listAvailable(int page, int size, Long userId) {
        // JDT 误报:LambdaQueryWrapper.eq/and(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        LambdaQueryWrapper<CouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(CouponEntity::getActive, true);
        q.and(w -> w.isNull(CouponEntity::getEndTime).or().ge(CouponEntity::getEndTime, LocalDateTime.now()));
        q.orderByDesc(CouponEntity::getCreateTime);
        Page<CouponEntity> result = couponMapper.selectPage(Page.of(page, size), q);
        // 防御：selectPage 在部分 MP 版本下 records 可能为 null，给个空列表避免 NPE
        if (result == null) {
            return new Page<>(page, size, 0);
        }
        if (result.getRecords() == null) {
            result.setRecords(java.util.Collections.emptyList());
        }
        // 已登录用户：批量查询其已领取的 couponId 集合，标记 claimedByMe
        if (userId != null && !result.getRecords().isEmpty()) {
            fillClaimedByMe(result.getRecords(), userId);
        }
        return result;
    }

    /**
     * 批量填充当前用户已领取标记，避免 N+1 查询
     */
    @SuppressWarnings("null")
    private void fillClaimedByMe(List<CouponEntity> coupons, Long userId) {
        List<Long> couponIds = coupons.stream().map(CouponEntity::getId).collect(Collectors.toList());
        if (couponIds.isEmpty()) return;
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
        // JDT 误报:LambdaQueryWrapper#eq/in(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        q.eq(UserCouponEntity::getUserId, userId).in(UserCouponEntity::getCouponId, couponIds);
        List<UserCouponEntity> userCoupons = userCouponMapper.selectList(q);
        Set<Long> claimedIds = userCoupons == null
                ? Collections.emptySet()
                : userCoupons.stream().map(UserCouponEntity::getCouponId).collect(Collectors.toCollection(HashSet::new));
        for (CouponEntity c : coupons) {
            c.setClaimedByMe(claimedIds.contains(c.getId()));
        }
    }

    @Override
    public CouponEntity getCouponDetail(Long id) {
        return couponMapper.selectById(id);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void claimCoupon(Long userId, Long couponId) {
        CouponEntity coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new IllegalArgumentException("优惠券不存在");
        if (!Boolean.TRUE.equals(coupon.getActive())) throw new IllegalArgumentException("该优惠券不可领取");
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("该优惠券已过期");

        // 先查一次,命中已领取时直接 409(业务可读 message),不依赖唯一索引兜底
        // JDT 误报:LambdaQueryWrapper#eq(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(UserCouponEntity::getUserId, userId).eq(UserCouponEntity::getCouponId, couponId);
        if (userCouponMapper.selectCount(q) > 0) {
            // 业务冲突:同一用户重复领取同一张券
            throw new com.moyuyo.common.exception.BusinessException(409, "您已领取过该优惠券");
        }

        // 库存校验
        if (coupon.getTotalCount() != null && coupon.getClaimedCount() != null
                && coupon.getClaimedCount() >= coupon.getTotalCount())
            throw new IllegalArgumentException("优惠券已领完");

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus("UNUSED");
        try {
            userCouponMapper.insert(uc);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发兜底:两次请求同时通过 selectCount 校验后,insert 撞 uk_user_coupon 唯一索引
            // 把数据库 409 转成业务 409,前端能拿到"已领取"语义
            log.info("[coupon] duplicate claim detected, userId={}, couponId={}", userId, couponId);
            throw new com.moyuyo.common.exception.BusinessException(409, "您已领取过该优惠券");
        }

        // 增加领取数
        coupon.setClaimedCount((coupon.getClaimedCount() == null ? 0 : coupon.getClaimedCount()) + 1);
        couponMapper.updateById(coupon);
    }

    @Override
    @SuppressWarnings("null")
    public List<UserCouponEntity> listUserCoupons(Long userId, String status) {
        // JDT 误报:LambdaQueryWrapper#eq/orderByDesc(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(UserCouponEntity::getUserId, userId);
        if (status != null && !status.isEmpty()) q.eq(UserCouponEntity::getStatus, status);
        q.orderByDesc(UserCouponEntity::getCreateTime);
        // 返回 user_coupon 记录本身（含 id/status），不再 map 成券模板而丢掉 uc：
        // 前端下单必须拿到 userCouponId(mo_user_coupon.id) 才能核销，原实现返回的模板列表没有该字段
        List<UserCouponEntity> ucs = userCouponMapper.selectList(q);
        return ucs == null ? Collections.emptyList() : ucs;
    }

    @Override
    @SuppressWarnings("null")
    public Map<Long, CouponEntity> mapCouponsByIds(List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量查 coupon，避免 N 条券 N 次单查的 N+1；distinct 去重后一次 IN 查询
        List<Long> distinctIds = couponIds.stream().filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return couponMapper.selectByIds(distinctIds).stream()
                .collect(Collectors.toMap(CouponEntity::getId, c -> c, (a, b) -> a));
    }

    @Override
    public UserCouponEntity getUserCoupon(Long userId, Long userCouponId) {
        if (userCouponId == null) {
            return null;
        }
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new IllegalArgumentException("用户优惠券不存在");
        }
        if (uc.getUserId() == null || !uc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用他人优惠券");
        }
        return uc;
    }

    /**
     * 下单占用优惠券：UNUSED → LOCKED（写 used_order_id），暂不置 USED。
     * <p>
     * 语义修正：券在用户"提交订单但尚未付款"阶段只是被**占用**，若直接置 USED，
     * 「我的优惠券」会立刻显示"已使用"，且支付成功时没有二次确认动作、取消时也无从区分。
     * 条件更新（WHERE status='UNUSED'）保证并发下同一张券只被一笔订单占用；
     * 抢不到时抛异常，由下单事务回滚整单（含已扣库存），避免双花。
     */
    @Override
    @Transactional
    @SuppressWarnings("null")
    public void useCoupon(Long userId, Long userCouponId, Long orderId) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) throw new IllegalArgumentException("用户优惠券不存在");
        if (!uc.getUserId().equals(userId)) throw new IllegalArgumentException("无权使用他人优惠券");
        // 条件更新防止并发双花：仅 UNUSED 能占用成功；失败说明已被其他订单占用/已核销
        // JDT 误报:LambdaUpdateWrapper#eq/set(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        int updated = userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getId, userCouponId)
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getStatus, "UNUSED")
                        .set(UserCouponEntity::getStatus, "LOCKED")
                        .set(UserCouponEntity::getUsedOrderId, orderId));
        if (updated == 0) {
            throw new IllegalStateException("该优惠券已被其他订单占用");
        }
    }

    /**
     * 支付成功核销优惠券：LOCKED → USED（写 used_time）。
     * <p>
     * 幂等：仅当该券仍由本订单占用时命中；重复回调 / 券已被释放时命中 0 行 → 仅记日志，不抛异常，
     * 避免把异常抛到支付回调主流程导致"钱已到账、订单状态却回滚"。
     */
    @Override
    @Transactional
    @SuppressWarnings("null")
    public void markCouponUsed(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            return;
        }
        int updated = userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getId, userCouponId)
                        .eq(UserCouponEntity::getStatus, "LOCKED")
                        .eq(UserCouponEntity::getUsedOrderId, orderId)
                        .set(UserCouponEntity::getStatus, "USED")
                        .set(UserCouponEntity::getUsedTime, LocalDateTime.now()));
        if (updated == 1) {
            log.info("Coupon marked used by payment: userCouponId={}, orderId={}", userCouponId, orderId);
        } else {
            // 幂等兜底：重复回调或券已被释放，不报错
            log.warn("Coupon mark-used skipped (not LOCKED or order mismatch): userCouponId={}, orderId={}",
                    userCouponId, orderId);
        }
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void releaseCoupon(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            return;
        }
        // 条件更新：仅当该券确由本订单占用(LOCKED & usedOrderId)时才返还，防止并发误还。
        // 幂等：重复取消 / 并发取消 / 券已 USED（已支付）时命中 0 行，不报错、不误还。
        // JDT 误报:LambdaUpdateWrapper#eq/set(SFunction, ...) 走 Function.apply(this) 路径,
        // JDT 推断 this 为 @Nonnull 产生冲突,实际仅反射列名,运行时无 NPE 风险
        int updated = userCouponMapper.update(null,
                new LambdaUpdateWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getId, userCouponId)
                        .eq(UserCouponEntity::getStatus, "LOCKED")
                        .eq(UserCouponEntity::getUsedOrderId, orderId)
                        .set(UserCouponEntity::getStatus, "UNUSED")
                        .set(UserCouponEntity::getUsedTime, null)
                        .set(UserCouponEntity::getUsedOrderId, null));
        if (updated == 1) {
            log.info("Coupon released by order cancel: userCouponId={}, orderId={}", userCouponId, orderId);
        }
    }

    @Override
    public BigDecimal computeCouponDiscount(Long userId, Long userCouponId, BigDecimal goodsAmount) {
        if (userCouponId == null) {
            return BigDecimal.ZERO; // 未使用优惠券
        }
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) {
            throw new IllegalArgumentException("用户优惠券不存在");
        }
        if (uc.getUserId() == null || !uc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用他人优惠券");
        }
        if (!"UNUSED".equals(uc.getStatus())) {
            throw new IllegalArgumentException("该优惠券不可使用");
        }
        CouponEntity coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null || !Boolean.TRUE.equals(coupon.getActive())) {
            throw new IllegalArgumentException("该优惠券不可使用");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("该优惠券已过期");
        }
        BigDecimal subtotal = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new IllegalArgumentException("未满足优惠券使用门槛（满 "
                    + coupon.getMinOrderAmount().setScale(2, RoundingMode.DOWN) + " 可用）");
        }
        BigDecimal discount;
        if ("PERCENT".equalsIgnoreCase(coupon.getType()) && coupon.getDiscountValue() != null) {
            // discountValue 存『价格百分比』(90 = 9 折 = 减扣 10%);
            // 减免 = subtotal × (1 - discountValue/100)
            BigDecimal rate = BigDecimal.ONE.subtract(coupon.getDiscountValue()
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            discount = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue() == null ? BigDecimal.ZERO : coupon.getDiscountValue();
        }
        // 防御：券配置异常导致折扣为负时按 0 处理，绝不给订单加价
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        // 封顶：优惠券自身上限 + 订单小计
        if (coupon.getMaxDiscountAmount() != null
                && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount;
    }

    @Override
    @Transactional
    public void transferCoupon(Long fromUserId, Long userCouponId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("不能转赠给自己");
        }
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) throw new IllegalArgumentException("用户优惠券不存在");
        if (!uc.getUserId().equals(fromUserId)) throw new IllegalArgumentException("无权转赠他人优惠券");
        if (!"UNUSED".equals(uc.getStatus())) throw new IllegalArgumentException("仅可转赠未使用优惠券");
        uc.setUserId(toUserId);
        userCouponMapper.updateById(uc);
        log.info("Coupon transferred: from={} to={} userCouponId={}", fromUserId, toUserId, userCouponId);
    }
}