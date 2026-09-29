package com.moyuyo.service.admin;

import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.OrderPrintLogEntity;
import com.moyuyo.dao.admin.mapper.OrderPrintLogMapper;
import com.moyuyo.dao.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单打印日志写入器（独立 Service Bean）。
 * <p>
 * 为什么单独建一个 Service 而不是直接写在 {@link AdminOrderOpsServiceImpl} 里？
 * <p>
 * Spring 的 {@code @Transactional} 基于代理 AOP 实现，<b>同类内部的方法调用</b>（如
 * {@code fetchShippingLabel()} 直接调用本类的 {@code recordShippingLabelLog()}）
 * <b>不会</b>经过代理，因此事务注解不生效。把它抽到独立的 Bean 后，跨 Bean 调用
 * 会走 Spring 代理，{@code @Transactional} 才能真正生效。
 * <p>
 * 这个 Bean 只负责短事务写入，不做其他业务逻辑（避免误把外部 HTTP 调用包进来）。
 */
@Service
@RequiredArgsConstructor
public class PrintLogWriter {

    private final OrderPrintLogMapper printLogMapper;

    /**
     * 记录一次燕文电子面单打印日志（独立事务，毫秒级）。
     * <p>
     * 与 {@link YanWenLabelService#fetchLabel} 调用必须分离，否则燕文 HTTP 调用
     * 会长时间占用数据库连接导致连接池耗尽。
     */
    @Transactional
    public void recordShippingLabelLog(OrderEntity order) {
        OrderPrintLogEntity entity = new OrderPrintLogEntity();
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setPrintType("SHIPPING_LABEL");
        entity.setTemplateName("燕文电子面单");
        entity.setPaperSize("thermal-100");
        entity.setOperator(UserContextHolder.getOperator() != null
                ? UserContextHolder.getOperator() : "系统");
        entity.setPrintCount(1);
        entity.setCreateTime(LocalDateTime.now());
        printLogMapper.insert(entity);
    }
}