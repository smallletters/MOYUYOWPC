package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.RefundChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefundServiceImpl.reversePointsOnRefund 单元测试
 * <p>
 * 通过反射调用 private 方法，覆盖以下场景：
 * - 正常全额退款（50% 抵扣积分、100% REWARD 全扣）
 * - 部分退款（按金额比例扣回）
 * - 订单未使用抵扣积分、无 REWARD（只跳过）
 * - 多条 REWARD 流水汇总
 * - 退款金额超过订单实付（ratio clamp 到 1.0）
 * - 边界：null 入参 / 零金额
 */
@ExtendWith(MockitoExtension.class)
class RefundPointsReverseTest {

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private RefundChannelService refundChannelService;

    @Mock
    private MemberService memberService;

    @Mock
    private PointsLogMapper pointsLogMapper;

    @InjectMocks
    private RefundServiceImpl refundService;

    @Captor
    private ArgumentCaptor<Integer> changeCaptor;

    @Captor
    private ArgumentCaptor<String> typeCaptor;

    private Method reverseMethod;

    @BeforeEach
    void setUp() throws Exception {
        // 通过反射拿到 private 方法，便于直接传入不同 order/refundAmount
        reverseMethod = RefundServiceImpl.class.getDeclaredMethod(
                "reversePointsOnRefund", OrderEntity.class, BigDecimal.class);
        reverseMethod.setAccessible(true);
    }

    @Test
    @DisplayName("全额退款：抵扣积分 200 原路返还 + REWARD 600 全扣")
    void fullRefund_returnAndClawback() throws Exception {
        OrderEntity order = buildOrder(1L, 100L, "30.00", 200);
        BigDecimal refundAmount = new BigDecimal("30.00");
        // 该订单有 2 条 REWARD 流水，正向：+500 +100 = 600
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildRewardLogs(100L, "ORDER-1", 500, 100));

        invoke(order, refundAmount);

        // 验证：2 次 addPoints 调用
        verify(memberService, times(2)).addPoints(eq(100L), changeCaptor.capture(),
                typeCaptor.capture(), any(), any());
        List<Integer> changes = changeCaptor.getAllValues();
        List<String> types = typeCaptor.getAllValues();
        // 调用顺序：先 REFUND_RETURN 再 REFUND_CLAWBACK
        assertEquals(200, changes.get(0));
        assertEquals("REFUND_RETURN", types.get(0));
        assertEquals(-600, changes.get(1));
        assertEquals("REFUND_CLAWBACK", types.get(1));
    }

    @Test
    @DisplayName("半额退款：抵扣 200 返还 100，REWARD 600 扣回 300")
    void halfRefund_proportionalReverse() throws Exception {
        OrderEntity order = buildOrder(2L, 100L, "30.00", 200);
        BigDecimal refundAmount = new BigDecimal("15.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildRewardLogs(100L, "ORDER-2", 600));

        invoke(order, refundAmount);

        verify(memberService, times(2)).addPoints(eq(100L), changeCaptor.capture(),
                typeCaptor.capture(), any(), any());
        List<Integer> changes = changeCaptor.getAllValues();
        // ratio = 15/30 = 0.5
        assertEquals(100, changes.get(0), "返还 200 × 0.5 = 100");
        assertEquals(-300, changes.get(1), "扣回 600 × 0.5 = 300");
    }

    @Test
    @DisplayName("无抵扣、无 REWARD：直接 return，不调用 addPoints")
    void noPointsNoReward_skip() throws Exception {
        OrderEntity order = buildOrder(3L, 100L, "30.00", 0);
        BigDecimal refundAmount = new BigDecimal("10.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        invoke(order, refundAmount);

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("退款金额大于订单实付：ratio clamp 到 1.0")
    void refundExceedsPayAmount_ratioClamped() throws Exception {
        OrderEntity order = buildOrder(4L, 100L, "30.00", 100);
        BigDecimal refundAmount = new BigDecimal("60.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildRewardLogs(100L, "ORDER-4", 300));

        invoke(order, refundAmount);

        verify(memberService, times(2)).addPoints(eq(100L), changeCaptor.capture(),
                any(), any(), any());
        List<Integer> changes = changeCaptor.getAllValues();
        assertEquals(100, changes.get(0), "抵扣 100 × 1.0 = 100（全额返还）");
        assertEquals(-300, changes.get(1), "REWARD 300 × 1.0 = -300（全部扣回）");
    }

    @Test
    @DisplayName("仅抵扣无 REWARD：只调用 REFUND_RETURN")
    void onlyUsedNoReward() throws Exception {
        OrderEntity order = buildOrder(5L, 100L, "30.00", 150);
        BigDecimal refundAmount = new BigDecimal("30.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        invoke(order, refundAmount);

        verify(memberService, times(1)).addPoints(eq(100L), eq(150), eq("REFUND_RETURN"),
                any(), any());
        verify(memberService, never()).addPoints(anyLong(), anyInt(), eq("REFUND_CLAWBACK"),
                any(), any());
    }

    @Test
    @DisplayName("仅 REWARD 无抵扣：只调用 REFUND_CLAWBACK")
    void onlyRewardNoUsed() throws Exception {
        OrderEntity order = buildOrder(6L, 100L, "30.00", 0);
        BigDecimal refundAmount = new BigDecimal("30.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(buildRewardLogs(100L, "ORDER-6", 300, 300, 300));

        invoke(order, refundAmount);

        verify(memberService, never()).addPoints(anyLong(), anyInt(), eq("REFUND_RETURN"),
                any(), any());
        verify(memberService, times(1)).addPoints(eq(100L), eq(-900), eq("REFUND_CLAWBACK"),
                any(), any());
    }

    @Test
    @DisplayName("订单 payAmount 为 0：直接 return")
    void zeroPayAmount_skip() throws Exception {
        OrderEntity order = buildOrder(7L, 100L, "0.00", 100);
        BigDecimal refundAmount = new BigDecimal("10.00");

        invoke(order, refundAmount);

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("refundAmount 为 0：直接 return")
    void zeroRefundAmount_skip() throws Exception {
        OrderEntity order = buildOrder(8L, 100L, "30.00", 100);

        invoke(order, BigDecimal.ZERO);

        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("order 为 null：直接 return")
    void nullOrder_skip() throws Exception {
        invoke(null, new BigDecimal("10.00"));
        verify(memberService, never()).addPoints(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("非整数比例四舍五入：33.33% => 抵扣 100 返 33")
    void roundingBehavior() throws Exception {
        OrderEntity order = buildOrder(9L, 100L, "30.00", 100);
        // 10 / 30 = 0.3333...
        BigDecimal refundAmount = new BigDecimal("10.00");
        when(pointsLogMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        invoke(order, refundAmount);

        verify(memberService, times(1)).addPoints(eq(100L), eq(33), eq("REFUND_RETURN"),
                any(), any());
    }

    /**
     * 反射调用 private 方法
     */
    private void invoke(OrderEntity order, BigDecimal refundAmount) throws Exception {
        reverseMethod.invoke(refundService, order, refundAmount);
    }

    /**
     * 构造订单实体
     */
    private OrderEntity buildOrder(Long id, Long userId, String payAmount, Integer pointsUsed) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo("ORDER-" + id);
        order.setPayAmount(new BigDecimal(payAmount));
        order.setPointsUsed(pointsUsed);
        return order;
    }

    /**
     * 构造正向 REWARD 流水列表
     */
    private List<PointsLogEntity> buildRewardLogs(Long userId, String bizNo, int... values) {
        List<PointsLogEntity> list = new ArrayList<>();
        for (int v : values) {
            PointsLogEntity e = new PointsLogEntity();
            e.setUserId(userId);
            e.setBizNo(bizNo);
            e.setType("REWARD");
            e.setChangeValue(v);
            list.add(e);
        }
        return list;
    }
}