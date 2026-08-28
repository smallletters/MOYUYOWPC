package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PointsRewardServiceImpl 单元测试：覆盖首单倍率、非首单、零金额、配置覆盖等关键分支
 */
@ExtendWith(MockitoExtension.class)
class PointsRewardServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private MemberService memberService;

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @InjectMocks
    private PointsRewardServiceImpl pointsRewardService;

    @Captor
    private ArgumentCaptor<Integer> pointsCaptor;

    @Captor
    private ArgumentCaptor<String> typeCaptor;

    @Test
    @DisplayName("首单 + 默认配置：1 USD = 10 积分，首单 2 倍 => 30 美元 = 600 积分")
    void rewardForOrder_firstOrder_defaultConfig() {
        OrderEntity order = buildOrder(1L, 100L, new BigDecimal("30.00"));
        // 首单判定：除当前订单外无其它 PAID 订单
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 配置缺失走默认值
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        int granted = pointsRewardService.rewardForOrder(order);

        assertEquals(600, granted, "30 USD × 10 汇率 × 2 倍 = 600");
        verify(memberService, times(1)).addPoints(eq(100L), eq(600), eq("REWARD"),
                eq("TEST-ORDER-1"), anyString());
    }

    @Test
    @DisplayName("非首单 + 默认配置：1 USD = 10 积分，倍率 1 => 30 美元 = 300 积分")
    void rewardForOrder_notFirstOrder_defaultConfig() {
        OrderEntity order = buildOrder(2L, 100L, new BigDecimal("30.00"));
        // 已有 1 笔 PAID 订单：非首单
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        int granted = pointsRewardService.rewardForOrder(order);

        assertEquals(300, granted, "30 USD × 10 汇率 × 1 倍 = 300");
        verify(memberService, times(1)).addPoints(eq(100L), eq(300), eq("REWARD"),
                eq("TEST-ORDER-2"), anyString());
    }

    @Test
    @DisplayName("配置覆盖：usd_rate=20 => 10 美元 = 200 积分（非首单）")
    void rewardForOrder_customRateFromConfig() {
        OrderEntity order = buildOrder(3L, 100L, new BigDecimal("10.00"));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        SystemConfigEntity rateCfg = new SystemConfigEntity();
        rateCfg.setConfigKey(PointsRewardServiceImpl.CONFIG_USD_RATE);
        rateCfg.setConfigValue("20");
        // 配置查询时按 key 返回对应记录
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(rateCfg);

        int granted = pointsRewardService.rewardForOrder(order);

        assertEquals(200, granted, "10 USD × 20 汇率 × 1 倍 = 200");
    }

    @Test
    @DisplayName("配置覆盖：first_order_multiplier=3 => 首单 10 美元 = 300 积分")
    void rewardForOrder_customMultiplierFromConfig() {
        OrderEntity order = buildOrder(4L, 100L, new BigDecimal("10.00"));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // 第一次查 usd_rate 返回 null（走默认 10），第二次查 first_order_multiplier 返回 3
        SystemConfigEntity multCfg = new SystemConfigEntity();
        multCfg.setConfigKey(PointsRewardServiceImpl.CONFIG_FIRST_ORDER_MULTIPLIER);
        multCfg.setConfigValue("3");
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(multCfg);

        int granted = pointsRewardService.rewardForOrder(order);

        assertEquals(300, granted, "10 USD × 10 汇率 × 3 倍 = 300");
    }

    @Test
    @DisplayName("实付金额为 0：不发放积分，且不调用 addPoints")
    void rewardForOrder_zeroAmount() {
        OrderEntity order = buildOrder(5L, 100L, BigDecimal.ZERO);

        int granted = pointsRewardService.rewardForOrder(order);

        assertEquals(0, granted);
        verify(memberService, never()).addPoints(anyLong(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("配置值非法：使用默认值，逻辑不中断")
    void rewardForOrder_invalidConfigValue_useDefault() {
        OrderEntity order = buildOrder(6L, 100L, new BigDecimal("5.00"));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        SystemConfigEntity badCfg = new SystemConfigEntity();
        badCfg.setConfigKey(PointsRewardServiceImpl.CONFIG_USD_RATE);
        badCfg.setConfigValue("not-a-number");
        when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(badCfg);

        int granted = pointsRewardService.rewardForOrder(order);

        // 5 × 10(默认) × 1 = 50
        assertEquals(50, granted);
    }

    @Test
    @DisplayName("订单为 null：直接返回 0，不抛异常")
    void rewardForOrder_nullOrder() {
        int granted = pointsRewardService.rewardForOrder(null);
        assertEquals(0, granted);
        verify(memberService, never()).addPoints(anyLong(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("remark 中包含实付金额与首单标记，便于运营核对")
    void rewardForOrder_remarkContainsPayAmountAndFirstFlag() {
        OrderEntity order = buildOrder(7L, 100L, new BigDecimal("15.00"));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        lenient().when(systemConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        pointsRewardService.rewardForOrder(order);

        verify(memberService).addPoints(anyLong(), pointsCaptor.capture(), typeCaptor.capture(),
                any(), anyString());
        assertEquals("REWARD", typeCaptor.getValue());
        // 15 USD × 10 × 2 = 300
        assertEquals(300, pointsCaptor.getValue());
    }

    /**
     * 构建一个已支付订单实体
     */
    private OrderEntity buildOrder(Long id, Long userId, BigDecimal payAmount) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo("TEST-ORDER-" + id);
        order.setPayAmount(payAmount);
        return order;
    }
}