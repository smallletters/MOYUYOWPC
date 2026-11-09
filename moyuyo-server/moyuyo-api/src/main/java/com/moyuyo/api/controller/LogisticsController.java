package com.moyuyo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.common.dto.logistics.ShipRequest;
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
public class LogisticsController {

    private final LogisticsService logisticsService;
    private final OrderService orderService;
    /** 复用 Spring 容器内的 ObjectMapper，避免每次请求都新建实例 */
    private final ObjectMapper objectMapper;

    @Operation(summary = "发货")
    @PostMapping("/ship")
    public Result<LogisticsVO> ship(@RequestBody ShipRequest request) {
        LogisticsEntity entity = logisticsService.shipOrder(
                request.getOrderId(), request.getCarrier(), request.getTrackingNumber());
        return Result.success(toLogisticsVO(entity));
    }

    @Operation(summary = "查询物流信息（用户）")
    @GetMapping("/order/{orderId}")
    public Result<LogisticsVO> getLogistics(@PathVariable Long orderId) {
        OrderEntity order = orderService.getOrderDetail(orderId, UserContextHolder.getUserId());
        LogisticsEntity entity = logisticsService.getLogisticsByOrderId(orderId);
        if (entity == null) {
            return Result.success(new LogisticsVO());
        }
        return Result.success(toLogisticsVO(entity));
    }

    @Operation(summary = "确认收货")
    @PostMapping("/{orderId}/confirm")
    public Result<Void> confirmReceived(@PathVariable Long orderId) {
        orderService.getOrderDetail(orderId, UserContextHolder.getUserId());
        logisticsService.confirmReceived(orderId);
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
