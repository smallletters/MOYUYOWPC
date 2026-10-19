package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.UserCouponEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    /**
     * 我的优惠券记录列表。
     * <p>
     * 返回的是 mo_user_coupon 记录本身（一条 user_coupon 一条记录，含 id / status / usedOrderId），
     * 而不是去重后的券模板；调用方（CouponController）再按 couponId 关联券模板组装展示视图，
     * 这样前端才能拿到 userCouponId 用于下单核销（原实现 map(uc -&gt; couponMap.get(...)) 丢掉了 uc 本身）。
     *
     * @param userId 当前用户
     * @param status 可选：精确匹配 mo_user_coupon.status（如 UNUSED / LOCKED / USED）；为空则不过滤
     */
    List<UserCouponEntity> listUserCoupons(Long userId, String status);

    /**
     * 按 id 批量查询券模板，避免"我的优惠券"列表 N 条券 N 次单查的 N+1。
     *
     * @param couponIds 券模板 id 集合；为空返回空 Map
     * @return couponId -&gt; CouponEntity
     */
    Map<Long, CouponEntity> mapCouponsByIds(List<Long> couponIds);

    /**
     * 按 userCouponId 取用户券记录并校验归属。
     *
     * @throws IllegalArgumentException 记录不存在或不属于该用户
     */
    UserCouponEntity getUserCoupon(Long userId, Long userCouponId);

    /**
     * 下单占用优惠券：UNUSED -&gt; LOCKED（写 used_order_id），非核销。
     * <p>
     * 采用条件更新（WHERE status='UNUSED'）防并发双花：抢不到（已被其它订单占用/已核销）时抛异常，
     * 由调用方所在的下单事务回滚整单。此时不应把券置为 USED——用户还没付款，券只是被"占用"。
     */
    void useCoupon(Long userId, Long userCouponId, Long orderId);

    /**
     * 支付成功核销优惠券：LOCKED -&gt; USED（写 used_time）。
     * <p>
     * 幂等：仅当该券仍由指定订单占用(LOCKED 且 usedOrderId=orderId)时才生效；重复回调/已被释放时静默跳过，
     * 不抛异常，避免阻断支付主流程。
     */
    void markCouponUsed(Long userCouponId, Long orderId);

    /**
     * 订单取消时返还占用的优惠券：仅当该券确由指定订单占用(LOCKED &amp; usedOrderId=orderId)时置回 UNUSED，
     * 并清空 usedOrderId/usedTime。userCouponId 为空直接忽略。
     * <p>
     * 幂等：重复取消 / 并发取消 / 券已不是 LOCKED 时条件更新命中 0 行，不报错、不误还。
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
