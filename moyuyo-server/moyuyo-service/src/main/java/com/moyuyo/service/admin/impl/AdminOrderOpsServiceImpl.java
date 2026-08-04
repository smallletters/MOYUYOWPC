package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import com.moyuyo.dao.admin.entity.OrderInterceptEntity;
import com.moyuyo.dao.admin.entity.OrderPriceModifyEntity;
import com.moyuyo.dao.admin.entity.OrderPrintLogEntity;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import com.moyuyo.dao.admin.mapper.OrderInterceptMapper;
import com.moyuyo.dao.admin.mapper.OrderPriceModifyMapper;
import com.moyuyo.dao.admin.mapper.OrderPrintLogMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminOrderOpsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
  private final OrderPriceModifyMapper priceModifyMapper;
  private final OrderInterceptMapper interceptMapper;
  private final OrderPrintLogMapper printLogMapper;
  private final DataExportRequestMapper exportRequestMapper;
  private static final Logger log = LoggerFactory.getLogger(AdminOrderOpsServiceImpl.class);

  @Override
  public Page<Map<String, Object>> listExport(String status, int page, int size) {
    // 从导出请求表查询导出任务列表
    LambdaQueryWrapper<DataExportRequestEntity> wrapper = new LambdaQueryWrapper<>();
    // 只查询订单导出类型的记录
    wrapper.in(DataExportRequestEntity::getRequestType, "订单导出", "ORDER_EXPORT");
    if (status != null && !status.isEmpty()) {
      // 状态映射：前端用中文，数据库存英文
      String dbStatus = switch (status) {
        case "进行中" -> "PROCESSING";
        case "已完成" -> "COMPLETED";
        case "失败" -> "FAILED";
        case "待处理" -> "PENDING";
        default -> status;
      };
      wrapper.eq(DataExportRequestEntity::getStatus, dbStatus);
    }
    wrapper.orderByDesc(DataExportRequestEntity::getCreateTime);

    Page<DataExportRequestEntity> entityPage = exportRequestMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("taskName", e.getTaskName());
      item.put("orderScope", e.getOrderScope());
      item.put("format", e.getFormat() != null ? e.getFormat() : "Excel");
      // 前端显示中文状态
      item.put("exportStatus", getExportStatusName(e.getStatus()));
      item.put("downloadUrl", e.getDownloadUrl());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Map<String, Object> stats() {
    QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
    wrapper.select("status", "COUNT(*) AS cnt").groupBy("status");
    List<Map<String, Object>> aggList = orderMapper.selectMaps(wrapper);

    Map<String, Long> statusStats = new LinkedHashMap<>();
    long totalOrders = 0;
    for (Map<String, Object> row : aggList) {
      Object statusObj = row.get("status");
      Object cntObj = row.get("cnt");
      if (statusObj != null && cntObj != null) {
        String status = statusObj.toString();
        long cnt = Long.parseLong(cntObj.toString());
        statusStats.put(status, cnt);
        totalOrders += cnt;
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalOrders", totalOrders);
    result.put("statusStats", statusStats);
    result.put("pendingPayment", statusStats.getOrDefault(OrderStatusEnum.PENDING_PAY.name(), 0L));
    result.put("pendingShip", statusStats.getOrDefault(OrderStatusEnum.PENDING_SHIP.name(), 0L));
    result.put("shipped", statusStats.getOrDefault(OrderStatusEnum.SHIPPED.name(), 0L));
    result.put("completed", statusStats.getOrDefault(OrderStatusEnum.COMPLETED.name(), 0L));
    result.put("cancelled", statusStats.getOrDefault(OrderStatusEnum.CANCELLED.name(), 0L));

    return result;
  }

  @Override
  public byte[] buildExportFile(String exportId) {
    // 根据导出标识查找任务，生成真实 CSV 内容
    DataExportRequestEntity task = exportRequestMapper.selectOne(
        new LambdaQueryWrapper<DataExportRequestEntity>()
            .eq(DataExportRequestEntity::getExportId, exportId)
            .last("LIMIT 1"));
    // 任务不存在时，导出空表头（保证下载不报错）
    StringBuilder sb = new StringBuilder();
    sb.append('\uFEFF'); // UTF-8 BOM，Excel 打开中文不乱码
    sb.append("订单号,状态,实付金额,币种,收货人,联系电话,收货地址,支付渠道,创建时间\n");
    if (task != null) {
      // 查询订单数据（按任务范围过滤：全部订单 / 本月订单 / 上周订单 / 自定义）
      LambdaQueryWrapper<OrderEntity> ow = new LambdaQueryWrapper<>();
      String scope = task.getOrderScope() == null ? "" : task.getOrderScope();
      if ("本月订单".equals(scope)) {
        ow.ge(OrderEntity::getCreateTime, java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay());
      } else if ("上周订单".equals(scope)) {
        ow.between(OrderEntity::getCreateTime,
            java.time.LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY).atStartOfDay(),
            java.time.LocalDate.now().minusWeeks(0).with(java.time.DayOfWeek.MONDAY).atStartOfDay());
      }
      ow.orderByDesc(OrderEntity::getCreateTime).last("LIMIT 500");
      List<OrderEntity> orders = orderMapper.selectList(ow);
      for (OrderEntity o : orders) {
        sb.append(escapeCsv(o.getOrderNo())).append(',')
            .append(escapeCsv(o.getStatus())).append(',')
            .append(o.getPayAmount() == null ? "" : o.getPayAmount().toPlainString()).append(',')
            .append(escapeCsv(o.getCurrency())).append(',')
            .append(escapeCsv(o.getReceiverName())).append(',')
            .append(escapeCsv(o.getReceiverPhone())).append(',')
            .append(escapeCsv(o.getReceiverAddress())).append(',')
            .append(escapeCsv(o.getPayChannel())).append(',')
            .append(o.getCreateTime() == null ? "" : o.getCreateTime().toString()).append('\n');
      }
    }
    return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /** CSV 字段转义：包含逗号/引号/换行时加引号包裹 */
  private String escapeCsv(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  @Override
  @Transactional
  public void batchShip(java.util.List<Long> ids, String carrier, String trackingNo) {
    ids.forEach(id -> {
      OrderEntity entity = orderMapper.selectById(id);
      if (entity != null) {
        // 已支付和待发货状态的订单均可批量发货，与单条发货逻辑保持一致
        if (!OrderStatusEnum.PENDING_SHIP.name().equals(entity.getStatus())
            && !OrderStatusEnum.PAID.name().equals(entity.getStatus())) {
          log.warn("订单 {} 状态为 {}，非可发货状态，跳过发货", id, entity.getStatus());
          return;
        }
        entity.setShippingCarrier(carrier);
        entity.setTrackingNumber(trackingNo);
        entity.setStatus(OrderStatusEnum.SHIPPED.name());
        orderMapper.updateById(entity);
      }
    });
  }

  @Override
  @Transactional
  public Map<String, Object> createExportTask(Map<String, Object> body) {
    String taskName = (String) body.getOrDefault("taskName", "订单导出");
    String orderScope = (String) body.getOrDefault("orderScope", "全部订单");
    String format = (String) body.getOrDefault("format", "Excel");

    DataExportRequestEntity entity = new DataExportRequestEntity();
    // 从当前登录用户上下文获取操作人ID，未获取到则使用系统用户ID
    Long currentUserId = UserContextHolder.getUserId();
    entity.setUserId(currentUserId != null ? currentUserId : 0L);
    entity.setExportId("EXPORT-" + System.currentTimeMillis());
    entity.setTaskName(taskName);
    entity.setOrderScope(orderScope);
    entity.setFormat(format);
    entity.setRequestType("ORDER_EXPORT");
    entity.setStatus("PENDING");
    entity.setCreateTime(LocalDateTime.now());
    exportRequestMapper.insert(entity);

    // 启动异步导出（简化：立即标记为完成并生成下载链接）
    generateExportFile(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", entity.getExportId());
    result.put("status", "PENDING");
    return result;
  }

  /**
   * 生成导出文件（异步执行，避免阻塞主线程）
   */
  @Async
  public void generateExportFile(DataExportRequestEntity entity) {
    try {
      // 模拟导出处理：等待一小段时间后标记完成
      Thread.sleep(500);

      // 实际场景应异步执行：查询订单 → 写入Excel/CSV → 上传到OSS → 记录下载链接
      String downloadUrl = "/api/admin/order-ops/export/download/" + entity.getExportId();
      entity.setStatus("COMPLETED");
      entity.setDownloadUrl(downloadUrl);
      entity.setCompleteTime(LocalDateTime.now());
      exportRequestMapper.updateById(entity);
    } catch (Exception e) {
      // 异常详情仅记录日志，不对外暴露，防止敏感信息泄露
      log.error("导出任务执行失败: {}", entity.getExportId(), e);
      entity.setStatus("FAILED");
      entity.setRemark("导出失败，请联系管理员查看日志");
      exportRequestMapper.updateById(entity);
    }
  }

  /**
   * 导出状态中文映射
   */
  private String getExportStatusName(String status) {
    if (status == null) return "待处理";
    return switch (status) {
      case "PENDING" -> "待处理";
      case "PROCESSING" -> "进行中";
      case "COMPLETED" -> "已完成";
      case "FAILED" -> "失败";
      default -> status;
    };
  }

  @Override
  @Transactional
  public void updateRemark(Long id, String remark) {
    OrderEntity entity = orderMapper.selectById(id);
    if (entity != null) {
      entity.setRemark(remark);
      orderMapper.updateById(entity);
    }
  }

  // ==================== 订单打印 ====================

  @Override
  public Page<Map<String, Object>> listPrint(String printType, int page, int size) {
    // 查询已发货的订单供打印列表使用
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name(), OrderStatusEnum.SHIPPED.name());
    wrapper.orderByDesc(OrderEntity::getCreateTime);

    Page<OrderEntity> entityPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderNo", e.getOrderNo());
      item.put("receiverName", e.getReceiverName());
      item.put("receiverPhone", e.getReceiverPhone());
      item.put("receiverAddress", e.getReceiverAddress());
      item.put("status", e.getStatus());
      // 查询打印次数
      LambdaQueryWrapper<OrderPrintLogEntity> printWrapper = new LambdaQueryWrapper<>();
      printWrapper.eq(OrderPrintLogEntity::getOrderId, e.getId());
      printWrapper.eq(printType != null && !printType.isEmpty(), OrderPrintLogEntity::getPrintType, printType);
      long printCount = printLogMapper.selectCount(printWrapper);
      item.put("printCount", printCount);
      item.put("printStatus", printCount > 0 ? "已打印" : "未打印");
      // 商品信息（从订单项查询）
      List<com.moyuyo.dao.entity.OrderItemEntity> items = orderItemMapper.selectByOrderId(e.getId());
      if (items != null && !items.isEmpty()) {
        item.put("productInfo", items.stream()
            .map(oi -> (oi.getProductName() != null ? oi.getProductName() : "") + " x" + oi.getQuantity())
            .collect(Collectors.joining("; ")));
      } else {
        item.put("productInfo", "");
      }
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  @Transactional
  public void recordPrint(Long orderId, String printType, String templateName, String paperSize, String operator) {
    OrderPrintLogEntity log = new OrderPrintLogEntity();
    log.setOrderId(orderId);
    // 查询订单号
    OrderEntity order = orderMapper.selectById(orderId);
    log.setOrderNo(order != null ? order.getOrderNo() : "");
    log.setPrintType(printType != null ? printType : "PICK");
    log.setTemplateName(templateName != null ? templateName : "默认模板");
    log.setPaperSize(paperSize != null ? paperSize : "A4");
    log.setOperator(operator != null ? operator : "系统");
    log.setPrintCount(1);
    log.setCreateTime(LocalDateTime.now());
    printLogMapper.insert(log);
  }

  // ==================== 订单改价 ====================

  @Override
  public Page<Map<String, Object>> listPriceModify(String keyword, Long orderId, int page, int size) {
    LambdaQueryWrapper<OrderPriceModifyEntity> wrapper = new LambdaQueryWrapper<>();
    // 支持按 orderId 或 orderNo 关键词查询
    if (orderId != null) {
      wrapper.eq(OrderPriceModifyEntity::getOrderId, orderId);
    } else if (keyword != null && !keyword.isEmpty()) {
      // 通过订单号查找 orderId
      List<OrderEntity> orders = orderMapper.selectList(
          new LambdaQueryWrapper<OrderEntity>()
              .like(OrderEntity::getOrderNo, keyword));
      if (orders.isEmpty()) {
        Page<Map<String, Object>> empty = new Page<>(page, size, 0);
        empty.setRecords(List.of());
        return empty;
      }
      List<Long> orderIds = orders.stream().map(OrderEntity::getId).collect(Collectors.toList());
      wrapper.in(OrderPriceModifyEntity::getOrderId, orderIds);
    }
    wrapper.orderByDesc(OrderPriceModifyEntity::getCreateTime);

    Page<OrderPriceModifyEntity> entityPage = priceModifyMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderId", e.getOrderId());
      item.put("orderNo", e.getOrderNo());
      // 查询商品信息
      item.put("product", getProductInfo(e.getOrderId()));
      item.put("originalAmount", e.getOriginalAmount());
      item.put("adjustAmount", e.getAdjustAmount());
      item.put("finalAmount", e.getFinalAmount());
      item.put("reason", e.getReason());
      item.put("reasonType", e.getReasonType());
      item.put("reasonTypeName", getReasonTypeName(e.getReasonType()));
      item.put("operator", e.getOperator());
      item.put("status", e.getStatus());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  @Transactional
  public void createPriceModify(Long orderId, String orderNo, BigDecimal originalAmount, BigDecimal adjustAmount,
                                String reason, String reasonType, String operator) {
    // 如果传入的是 orderNo，查找对应的 orderId
    if (orderId == null && orderNo != null) {
      OrderEntity order = orderMapper.selectOne(
          new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getOrderNo, orderNo));
      if (order == null) {
        throw new IllegalArgumentException("订单不存在: " + orderNo);
      }
      orderId = order.getId();
      if (originalAmount == null) {
        originalAmount = order.getPayAmount();
      }
    }
    if (orderId == null) {
      throw new IllegalArgumentException("必须提供订单ID或订单编号");
    }

    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }

    OrderPriceModifyEntity entity = new OrderPriceModifyEntity();
    entity.setOrderId(orderId);
    entity.setOrderNo(order.getOrderNo());
    entity.setOriginalAmount(originalAmount != null ? originalAmount : order.getPayAmount());
    entity.setAdjustAmount(adjustAmount);
    entity.setFinalAmount(entity.getOriginalAmount().add(adjustAmount));
    entity.setReason(reason);
    entity.setReasonType(reasonType);
    entity.setOperator(operator);
    entity.setStatus("APPROVED"); // 简化：直接通过，实际需要审批流
    entity.setCreateTime(LocalDateTime.now());
    priceModifyMapper.insert(entity);

    // 更新订单金额
    order.setPayAmount(entity.getFinalAmount());
    orderMapper.updateById(order);
  }

  private String getReasonTypeName(String type) {
    if (type == null) return "人工优惠";
    return switch (type) {
      case "FREIGHT" -> "补运费";
      case "DISCOUNT" -> "减差价";
      case "MANUAL" -> "人工优惠";
      default -> type;
    };
  }

  /**
   * 获取订单的商品信息文本
   */
  private String getProductInfo(Long orderId) {
    try {
      List<OrderItemEntity> items = orderItemMapper.selectByOrderId(orderId);
      if (items != null && !items.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (OrderItemEntity oi : items) {
          if (sb.length() > 0) sb.append("; ");
          sb.append(oi.getProductName() != null ? oi.getProductName() : "").append(" x").append(oi.getQuantity());
        }
        return sb.toString();
      }
    } catch (Exception ignored) {}
    return "";
  }

  // ==================== 订单拦截 ====================

  @Override
  public Page<Map<String, Object>> listIntercept(String status, int page, int size) {
    LambdaQueryWrapper<OrderInterceptEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(OrderInterceptEntity::getStatus, status);
    }
    wrapper.orderByDesc(OrderInterceptEntity::getCreateTime);

    Page<OrderInterceptEntity> entityPage = interceptMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      // 关联订单信息
      OrderEntity order = orderMapper.selectById(e.getOrderId());
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderId", e.getOrderId());
      item.put("orderNo", e.getOrderNo());
      item.put("interceptType", e.getInterceptType());
      item.put("interceptTypeName", getInterceptTypeName(e.getInterceptType()));
      item.put("reason", e.getReason());
      item.put("reasonTemplate", e.getReasonTemplate());
      item.put("operator", e.getOperator());
      item.put("status", e.getStatus());
      item.put("releaseReason", e.getReleaseReason());
      item.put("releaseOperator", e.getReleaseOperator());
      item.put("releaseTime", e.getReleaseTime());
      item.put("createTime", e.getCreateTime());
      item.put("currentStatus", order != null ? order.getStatus() : "未知");
      item.put("amount", order != null ? order.getPayAmount() : null);
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  @Transactional
  public void createIntercept(Long orderId, String interceptType, String reason, String reasonTemplate, String operator) {
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }

    OrderInterceptEntity entity = new OrderInterceptEntity();
    entity.setOrderId(orderId);
    entity.setOrderNo(order.getOrderNo());
    entity.setInterceptType(interceptType != null ? interceptType : "MANUAL");
    entity.setReason(reason);
    entity.setReasonTemplate(reasonTemplate);
    entity.setOperator(operator);
    entity.setStatus("ACTIVE");
    entity.setCreateTime(LocalDateTime.now());
    interceptMapper.insert(entity);

    // 更新订单状态为已拦截（使用HOLD暂存状态）
    order.setStatus("HOLD");
    orderMapper.updateById(order);
  }

  @Override
  @Transactional
  public void releaseIntercept(Long interceptId, String releaseReason, String releaseOperator) {
    OrderInterceptEntity entity = interceptMapper.selectById(interceptId);
    if (entity == null) {
      throw new IllegalArgumentException("拦截记录不存在: " + interceptId);
    }
    if (!"ACTIVE".equals(entity.getStatus())) {
      throw new IllegalStateException("拦截记录已解除");
    }

    entity.setStatus("RELEASED");
    entity.setReleaseReason(releaseReason);
    entity.setReleaseOperator(releaseOperator);
    entity.setReleaseTime(LocalDateTime.now());
    interceptMapper.updateById(entity);

    // 恢复订单状态为待发货
    OrderEntity order = orderMapper.selectById(entity.getOrderId());
    if (order != null && "HOLD".equals(order.getStatus())) {
      order.setStatus(OrderStatusEnum.PENDING_SHIP.name());
      orderMapper.updateById(order);
    }
  }

  private String getInterceptTypeName(String type) {
    if (type == null) return "人工";
    return switch (type) {
      case "RISK" -> "风控";
      case "MANUAL" -> "人工";
      case "SYSTEM" -> "系统";
      default -> type;
    };
  }

  // ==================== 订单监控 ====================

  @Override
  public Map<String, Object> getMonitorData() {
    Map<String, Object> result = new LinkedHashMap<>();

    // 今日订单统计
    LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
    LambdaQueryWrapper<OrderEntity> todayWrapper = new LambdaQueryWrapper<>();
    todayWrapper.ge(OrderEntity::getCreateTime, todayStart);
    long todayOrders = orderMapper.selectCount(todayWrapper);
    result.put("todayOrders", todayOrders);

    // 待发货订单
    LambdaQueryWrapper<OrderEntity> shipWrapper = new LambdaQueryWrapper<>();
    shipWrapper.eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name());
    long pendingShip = orderMapper.selectCount(shipWrapper);
    result.put("pendingShip", pendingShip);

    // 异常订单（超时未付款 - 超过24小时的待付款订单）
    LocalDateTime timeoutPay = LocalDateTime.now().minusHours(24);
    LambdaQueryWrapper<OrderEntity> timeoutPaymentWrapper = new LambdaQueryWrapper<>();
    timeoutPaymentWrapper.eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAY.name())
        .le(OrderEntity::getCreateTime, timeoutPay);
    long timeoutPayment = orderMapper.selectCount(timeoutPaymentWrapper);
    result.put("timeoutPayment", timeoutPayment);

    // 异常订单（超时未发货 - 超过72小时的待发货订单）
    LocalDateTime timeoutShip = LocalDateTime.now().minusHours(72);
    LambdaQueryWrapper<OrderEntity> timeoutShipWrapper = new LambdaQueryWrapper<>();
    timeoutShipWrapper.eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name())
        .le(OrderEntity::getCreateTime, timeoutShip);
    long timeoutShipCount = orderMapper.selectCount(timeoutShipWrapper);
    result.put("timeoutShip", timeoutShipCount);

    // 被拦截中的订单数
    LambdaQueryWrapper<OrderInterceptEntity> interceptWrapper = new LambdaQueryWrapper<>();
    interceptWrapper.eq(OrderInterceptEntity::getStatus, "ACTIVE");
    long intercepting = interceptMapper.selectCount(interceptWrapper);
    result.put("intercepting", intercepting);

    // 异常订单总数
    result.put("abnormalOrders", timeoutPayment + timeoutShipCount + intercepting);

    return result;
  }

  @Override
  public Page<Map<String, Object>> listAbnormalOrders(String abnormalType, int page, int size) {
    Page<OrderEntity> resultPage = new Page<>(page, size);

    if ("timeoutPayment".equals(abnormalType)) {
      // 超时未付款
      LocalDateTime timeout = LocalDateTime.now().minusHours(24);
      LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAY.name())
          .le(OrderEntity::getCreateTime, timeout)
          .orderByAsc(OrderEntity::getCreateTime);
      resultPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    } else if ("timeoutShip".equals(abnormalType)) {
      // 超时未发货
      LocalDateTime timeout = LocalDateTime.now().minusHours(72);
      LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name())
          .le(OrderEntity::getCreateTime, timeout)
          .orderByAsc(OrderEntity::getCreateTime);
      resultPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    } else {
      // 全部异常
      LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
      wrapper.in(OrderEntity::getStatus, "HOLD", OrderStatusEnum.CANCELLED.name())
          .orderByDesc(OrderEntity::getCreateTime);
      resultPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    }

    Page<Map<String, Object>> mappedPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
    mappedPage.setRecords(resultPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderNo", e.getOrderNo());
      item.put("status", e.getStatus());
      item.put("payAmount", e.getPayAmount());
      item.put("receiverName", e.getReceiverName());
      item.put("receiverPhone", e.getReceiverPhone());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return mappedPage;
  }
}
