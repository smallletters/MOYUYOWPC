package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    public Page<CouponEntity> listAvailable(int page, int size, Long userId) {
        LambdaQueryWrapper<CouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(CouponEntity::getActive, true);
        q.and(w -> w.isNull(CouponEntity::getEndTime).or().ge(CouponEntity::getEndTime, LocalDateTime.now()));
        q.orderByDesc(CouponEntity::getCreateTime);
        Page<CouponEntity> result = couponMapper.selectPage(Page.of(page, size), q);
        // 已登录用户：批量查询其已领取的 couponId 集合，标记 claimedByMe
        if (userId != null && result != null && !result.getRecords().isEmpty()) {
            fillClaimedByMe(result.getRecords(), userId);
        }
        return result;
    }

    /**
     * 批量填充当前用户已领取标记，避免 N+1 查询
     */
    private void fillClaimedByMe(List<CouponEntity> coupons, Long userId) {
        List<Long> couponIds = coupons.stream().map(CouponEntity::getId).collect(Collectors.toList());
        if (couponIds.isEmpty()) return;
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
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
    public void claimCoupon(Long userId, Long couponId) {
        CouponEntity coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new IllegalArgumentException("优惠券不存在");
        if (!Boolean.TRUE.equals(coupon.getActive())) throw new IllegalArgumentException("该优惠券不可领取");
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("该优惠券已过期");

        // 防止重复领取
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(UserCouponEntity::getUserId, userId).eq(UserCouponEntity::getCouponId, couponId);
        if (userCouponMapper.selectCount(q) > 0) throw new IllegalArgumentException("您已领取过该优惠券");

        // 库存校验
        if (coupon.getTotalCount() != null && coupon.getClaimedCount() != null
                && coupon.getClaimedCount() >= coupon.getTotalCount())
            throw new IllegalArgumentException("优惠券已领完");

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus("UNUSED");
        userCouponMapper.insert(uc);

        // 增加领取数
        coupon.setClaimedCount((coupon.getClaimedCount() == null ? 0 : coupon.getClaimedCount()) + 1);
        couponMapper.updateById(coupon);
    }

    @Override
    public List<CouponEntity> listUserCoupons(Long userId, String status) {
        LambdaQueryWrapper<UserCouponEntity> q = new LambdaQueryWrapper<>();
        q.eq(UserCouponEntity::getUserId, userId);
        if (status != null && !status.isEmpty()) q.eq(UserCouponEntity::getStatus, status);
        q.orderByDesc(UserCouponEntity::getCreateTime);
        List<UserCouponEntity> ucs = userCouponMapper.selectList(q);
        return ucs.stream().map(uc -> couponMapper.selectById(uc.getCouponId())).toList();
    }

    @Override
    @Transactional
    public void useCoupon(Long userId, Long userCouponId, Long orderId) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) throw new IllegalArgumentException("用户优惠券不存在");
        if (!uc.getUserId().equals(userId)) throw new IllegalArgumentException("无权使用他人优惠券");
        if (!"UNUSED".equals(uc.getStatus())) throw new IllegalArgumentException("该优惠券不可使用");
        uc.setStatus("USED");
        uc.setUsedTime(LocalDateTime.now());
        uc.setUsedOrderId(orderId);
        userCouponMapper.updateById(uc);
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