package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 消费返积分服务
 * <p>
 * 规则：
 * - 汇率：每 1 美元 = 10 积分（可通过 mo_system_config.points.usd_rate 覆盖）
 * - 首单倍率：新用户首单默认 2 倍积分（可通过 points.first_order_multiplier 覆盖）
 * - 触发时机：订单支付成功（payCallback）
 * - 流水类型：REWARD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsRewardServiceImpl {

    /** 默认汇率：1 USD = 10 积分 */
    private static final int DEFAULT_USD_RATE = 10;
    /** 默认首单倍率 */
    private static final int DEFAULT_FIRST_ORDER_MULTIPLIER = 2;

    /** 配置项 key：每美元多少积分 */
    public static final String CONFIG_USD_RATE = "points.usd_rate";
    /** 配置项 key：首单积分倍率 */
    public static final String CONFIG_FIRST_ORDER_MULTIPLIER = "points.first_order_multiplier";

    private final OrderMapper orderMapper;
    private final MemberService memberService;
    private final SystemConfigMapper systemConfigMapper;

    /**
     * 发放订单消费返积分
     *
     * @param order 已支付订单
     * @return 实际发放积分数；非首单/无金额等场景返回 0
     */
    @Transactional
    public int rewardForOrder(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return 0;
        }
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.signum() <= 0) {
            return 0;
        }

        int rate = readIntConfig(CONFIG_USD_RATE, DEFAULT_USD_RATE);
        // 基础积分：payAmount 已是美元，按整元计算
        int basePoints = payAmount.intValue() * rate;
        if (basePoints <= 0) {
            return 0;
        }

        boolean isFirstOrder = isFirstPaidOrder(order.getUserId(), order.getId());
        int multiplier = isFirstOrder
                ? readIntConfig(CONFIG_FIRST_ORDER_MULTIPLIER, DEFAULT_FIRST_ORDER_MULTIPLIER)
                : 1;
        int finalPoints = basePoints * multiplier;

        // 写积分流水（REWARD），addPoints 内部会自动重算会员等级
        memberService.addPoints(
                order.getUserId(),
                finalPoints,
                "REWARD",
                order.getOrderNo(),
                String.format("消费返积分：订单 %s，实付 %.2f 美元，首单=%s，倍率=%d，汇率=%d",
                        order.getOrderNo(),
                        payAmount,
                        isFirstOrder,
                        multiplier,
                        rate));
        log.info("Points reward granted: userId={}, orderNo={}, payAmount={}, basePoints={}, multiplier={}, finalPoints={}",
                order.getUserId(), order.getOrderNo(), payAmount, basePoints, multiplier, finalPoints);
        return finalPoints;
    }

    /**
     * 判断用户是否首单（当前订单之前没有任何已支付订单）
     */
    private boolean isFirstPaidOrder(Long userId, Long currentOrderId) {
        Long count = orderMapper.selectCount(
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getUserId, userId)
                        .eq(OrderEntity::getStatus, OrderStatusEnum.PAID.name())
                        .ne(OrderEntity::getId, currentOrderId));
        return count == null || count == 0;
    }

    /**
     * 读取整数配置项，缺失/格式非法时返回默认值
     */
    private int readIntConfig(String key, int defaultValue) {
        try {
            SystemConfigEntity entity = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfigEntity>()
                            .eq(SystemConfigEntity::getConfigKey, key));
            if (entity == null || entity.getConfigValue() == null || entity.getConfigValue().isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(entity.getConfigValue().trim());
        } catch (Exception e) {
            log.warn("读取积分配置 {} 失败，使用默认值 {}: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }
}