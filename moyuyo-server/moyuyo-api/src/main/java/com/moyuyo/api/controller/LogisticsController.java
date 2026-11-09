package com.moyuyo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Tag(name = "物流管理")
@RestController
@RequestMapping("/api/v1/logistics")
@RequiredArgsConstructor
// 安全约束：C 端仅提供"查物流 / 确认收货"，两者都必须先校验订单归属。
// 发货（写运单号并推进订单到 SHIPPED）属于管理端操作，只允许 /api/admin/orders/{id}/ship，
// 禁止在 C 端重新暴露，避免任意登录用户篡改他人订单状态。
public class LogisticsController {

    private final LogisticsService logisticsService;
    private final OrderService orderService;
    /** 复用 Spring 容器内的 ObjectMapper，避免每次请求都新建实例 */
    private final ObjectMapper objectMapper;

    @Operation(summary = "查询物流信息（用户）")
    @GetMapping("/order/{orderId}")
    public Result<LogisticsVO> getLogistics(@PathVariable Long orderId) {
        OrderEntity order = orderService.getOrderDetail(orderId, UserContextHolder.getUserId());
        // getOrderDetail 对"订单不存在"返回 null（不抛异常），此处必须显式拦住，
        // 否则会返回 200 + 空物流，与订单详情接口的 404 口径不一致
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        LogisticsEntity entity = logisticsService.getLogisticsByOrderId(orderId);
        if (entity == null) {
            return Result.success(new LogisticsVO());
        }
        return Result.success(toLogisticsVO(entity));
    }

    @Operation(summary = "确认收货")
    @PostMapping("/{orderId}/confirm")
    public Result<Void> confirmReceived(@PathVariable Long orderId) {
        OrderEntity order = orderService.getOrderDetail(orderId, UserContextHolder.getUserId());
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        // 条件更新失败（订单非"已发货"）时不能返回成功，否则前端提示"已确认收货"但状态未变
        if (!logisticsService.confirmReceived(orderId)) {
            return Result.error(409, "当前订单状态不允许确认收货");
        }
        return Result.success();
    }

    private LogisticsVO toLogisticsVO(LogisticsEntity entity) {
        LogisticsVO vo = new LogisticsVO();
        vo.setId(entity.getId());
        vo.setOrderId(entity.getOrderId());
        vo.setCarrier(entity.getCarrier());
        vo.setTrackingNumber(entity.getTrackingNumber());
        vo.setShippedAt(entity.getShippedAt());
        vo.setReceivedAt(entity.getReceivedAt());

        if (entity.getTraces() != null && !entity.getTraces().isBlank()) {
            try {
                List<LogisticsVO.TraceItem> traces = objectMapper.readValue(entity.getTraces(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, LogisticsVO.TraceItem.class));
                vo.setTraces(traces);
            } catch (Exception e) {
                vo.setTraces(Collections.emptyList());
            }
        } else {
            vo.setTraces(Collections.emptyList());
        }

        if (entity.getReceivedAt() != null) vo.setCurrentStatus("DELIVERED");
        else if (entity.getShippedAt() != null) vo.setCurrentStatus("IN_TRANSIT");
        else vo.setCurrentStatus("PENDING");

        return vo;
    }
}
