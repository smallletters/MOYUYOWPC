package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.AdminOrderOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台订单运营服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminOrderOpsServiceImpl implements AdminOrderOpsService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;

  @Override
  public Page<Map<String, Object>> listExport(String status, int page, int size) {
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(OrderEntity::getStatus, status);
    }
    wrapper.orderByDesc(OrderEntity::getCreateTime);

    Page<OrderEntity> entityPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderNo", e.getOrderNo());
      item.put("userId", e.getUserId());
      item.put("payAmount", e.getPayAmount());
      item.put("status", e.getStatus());
      item.put("receiverName", e.getReceiverName());
      item.put("receiverPhone", e.getReceiverPhone());
      item.put("receiverAddress", e.getReceiverAddress());
      item.put("trackingNumber", e.getTrackingNumber());
      item.put("shippingCarrier", e.getShippingCarrier());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Map<String, Object> stats() {
    List<OrderEntity> all = orderMapper.selectList(null);
    Map<String, Object> result = new LinkedHashMap<>();

    // 总数
    result.put("totalOrders", all.size());

    // 按状态统计
    Map<String, Long> statusStats = all.stream()
        .collect(Collectors.groupingBy(OrderEntity::getStatus, Collectors.counting()));
    result.put("statusStats", statusStats);

    // 常见状态字段
    result.put("pendingPayment", statusStats.getOrDefault("PENDING_PAYMENT", 0L));
    result.put("pendingShip", statusStats.getOrDefault("PENDING_SHIP", 0L));
    result.put("shipped", statusStats.getOrDefault("SHIPPED", 0L));
    result.put("completed", statusStats.getOrDefault("COMPLETED", 0L));
    result.put("cancelled", statusStats.getOrDefault("CANCELLED", 0L));

    return result;
  }

  @Override
  public void batchShip(List<Long> ids, String carrier, String trackingNo) {
    ids.forEach(id -> {
      OrderEntity entity = orderMapper.selectById(id);
      if (entity != null) {
        entity.setShippingCarrier(carrier);
        entity.setTrackingNumber(trackingNo);
        entity.setStatus("SHIPPED");
        orderMapper.updateById(entity);
      }
    });
  }

  @Override
  public void updateRemark(Long id, String remark) {
    OrderEntity entity = orderMapper.selectById(id);
    if (entity != null) {
      entity.setRemark(remark);
      orderMapper.updateById(entity);
    }
  }
}
