package com.moyuyo.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 * <p>
 * 每分钟扫描一次"创建超过超时阈值（默认 30 分钟）且仍处于 PENDING_PAY 状态"的订单，
 * 调用 {@link OrderService#cancelOrder(Long, Long, String)} 走取消流程（恢复库存 + 状态流转）。
 * <p>
 * 设计取舍：项目原有 OrderMessageProducer + OrderTimeoutConsumer MQ 链路在生产环境中无人投递消息（H3 bug），
 * 此处用定时任务兜底，避免 MQ 不可用或未配置 RocketMQ 时订单永远卡在 PENDING_PAY。
 * <p>
 * 单次批量处理上限 batchSize（默认 200）防止 DB 抖动；超过阈值时分批处理。
 * <p>
 * 总开关 moyuyo.order.timeout.enabled，默认 true，dev 环境可关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelJob {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Value("${moyuyo.order.timeout.enabled:true}")
    private boolean enabled;

    /** 超时阈值：默认 30 分钟，与订单创建时约定的支付窗口一致 */
    @Value("${moyuyo.order.timeout-minutes:30}")
    private int timeoutMinutes;

    /** 单次扫描上限：防止 DB 大表全扫 / 抖动 */
    @Value("${moyuyo.order.timeout-batch-size:200}")
    private int batchSize;

    /**
     * 每分钟执行一次。
     * <p>
     * fixedDelay 而非 cron：上一次任务结束后再等 60 秒，避免长时间运行时积压。
     */
    @Scheduled(fixedDelayString = "${moyuyo.order.timeout-fixed-delay-ms:60000}", initialDelay = 30000)
    public void cancelTimeoutOrders() {
        if (!enabled) {
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
            // 仅查 PENDING_PAY 状态的订单，删除状态 0 表示未删除
            List<OrderEntity> expired = orderMapper.selectList(
                    new LambdaQueryWrapper<OrderEntity>()
                            .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAY.name())
                            .eq(OrderEntity::getDeleteStatus, 0)
                            .lt(OrderEntity::getCreateTime, threshold)
                            .last("LIMIT " + Math.max(1, batchSize)));
            if (expired.isEmpty()) {
                return;
            }
            log.info("[order-timeout] 扫描到 {} 笔超时未支付订单，开始自动取消", expired.size());
            int success = 0;
            for (OrderEntity order : expired) {
                try {
                    // userId=null 走管理员绕过权限校验；reason 固定便于审计追溯
                    orderService.cancelOrder(order.getId(), null, "订单超时自动取消");
                    success++;
                } catch (Exception e) {
                    log.warn("[order-timeout] 取消订单失败 orderId={}, reason={}",
                            order.getId(), e.getMessage());
                }
            }
            log.info("[order-timeout] 自动取消完成：成功 {} / 总 {} 笔", success, expired.size());
        } catch (Exception e) {
            // 任何异常都不能让定时任务挂掉，下一分钟继续
            log.error("[order-timeout] 定时任务异常", e);
        }
    }
}