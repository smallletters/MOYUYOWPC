package com.moyuyo.service.admin;

import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 使用 JdbcTemplate 而非 Mapper.insert —— 后者无法表达
     * "INSERT ... ON DUPLICATE KEY UPDATE print_count = print_count + 1" 的原子累加 SQL。
     * <p>
     * 与 AdminOrderOpsServiceImpl.recordPrint 共用同一份 upsert SQL，保证两条路径行为一致。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 记录一次燕文电子面单打印日志（独立事务，毫秒级；与 printType+templateName+paperSize 维度合并计数）。
     * <p>
     * 与 {@link YanWenLabelService#fetchLabel} 调用必须分离，否则燕文 HTTP 调用
     * 会长时间占用数据库连接导致连接池耗尽。
     * <p>
     * 关键修复：原实现每次都 insert（无唯一索引保护），同一订单重复取号会无限堆记录。
     * 配合 V20260929_04__print_log_unique_index.sql 加的唯一索引，本方法用
     * ON DUPLICATE KEY UPDATE 原子累加 print_count，不再产生记录重复。
     */
    @Transactional
    public void recordShippingLabelLog(OrderEntity order) {
        String operator = UserContextHolder.getOperator() != null
                ? UserContextHolder.getOperator() : "系统";
        String upsertSql =
            "INSERT INTO mo_order_print_log " +
            "(order_id, order_no, print_type, template_name, paper_size, operator, print_count, create_time, update_time) " +
            "VALUES (?, ?, 'SHIPPING_LABEL', '燕文电子面单', 'thermal-100', ?, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "  print_count = print_count + 1, " +
            "  update_time = NOW()";
        jdbcTemplate.update(upsertSql, order.getId(), order.getOrderNo(), operator);
    }
}