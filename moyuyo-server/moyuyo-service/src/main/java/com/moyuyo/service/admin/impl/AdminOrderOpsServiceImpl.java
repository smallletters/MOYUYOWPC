package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.DataExportRequestEntity;
import com.moyuyo.dao.admin.entity.OrderInterceptEntity;
import com.moyuyo.dao.admin.entity.OrderPriceModifyEntity;
import com.moyuyo.dao.admin.entity.PrintTemplateEntity;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import com.moyuyo.dao.admin.mapper.OrderInterceptMapper;
import com.moyuyo.dao.admin.mapper.OrderPriceModifyMapper;
import com.moyuyo.dao.admin.mapper.PrintTemplateMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.exception.OrderStatusGuard;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminOrderOpsService;
import com.moyuyo.service.admin.YanWenLabelService;
import com.moyuyo.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台订单运营服务实现
 */
// 抑制 JDT null-analysis 对 MyBatis-Plus SFunction / Stream 方法引用的误报
// （底层 SFunction 的 @Nonnull 类型参数 vs Function.apply 形参推断冲突，mvn 编译无影响）
@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
public class AdminOrderOpsServiceImpl implements AdminOrderOpsService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final OrderPriceModifyMapper priceModifyMapper;
  private final OrderInterceptMapper interceptMapper;
  private final PrintTemplateMapper printTemplateMapper;
  private final DataExportRequestMapper exportRequestMapper;
  private final JdbcTemplate jdbcTemplate;
  private final YanWenLabelService yanWenLabelService;
  // 用于在打印列表 / 打印详情里回填历史订单缺失的收货快照（与 OrderServiceImpl.getOrderDetail 行为一致）
  private final OrderService orderService;
  // P1：打印设置 JSON 序列化
  private final ObjectMapper objectMapper;
  // 独立 Bean：用于跨 Bean 调用以触发事务代理（同类内部调用 @Transactional 不生效）
  private final com.moyuyo.service.admin.PrintLogWriter printLogWriter;
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
      // P0：批量发货幂等 —— INSERT IGNORE 抢锁，与 shipOrder 共用同一 scope + 同一指纹算法。
      // 注意：批量循环在同一 @Transactional 内执行。
      //   - 抢锁失败的 id 直接跳过（return），不影响其它订单。
      //   - 抢锁成功但状态校验失败的 id 抛错回滚整个事务 —— 批量场景下"半成功"对运营更危险。
      String idemKey = com.moyuyo.common.util.IdempotencyHelper.shipFingerprint(id, carrier, trackingNo);
      int inserted = jdbcTemplate.update(
          "INSERT IGNORE INTO mo_idempotency_record (scope, biz_id, idem_key, response_json) VALUES (?, ?, ?, ?)",
          "ship_order", id, idemKey, "{\"orderId\":" + id + "}");
      if (inserted == 0) {
        log.info("batchShip 幂等命中（与 shipOrder 同键），跳过：orderId={}, key={}", id, idemKey);
        return;
      }
      OrderEntity entity = orderMapper.selectById(id);
      if (entity != null) {
        // P0：使用 OrderStatusGuard 统一校验发货源状态，
        // 防止对已发货/已退款订单重复发货。
        // 批量场景下若状态非法直接抛错回滚（不在循环里吞掉），
        // 避免运营以为已发货实际写库失败。
        try {
          OrderStatusGuard.assertAllowed(entity.getStatus(), OrderStatusGuard.SHIP_FROM, "批量发货");
        } catch (com.moyuyo.common.exception.BusinessException e) {
          log.warn("订单 {} 状态 {} 不允许批量发货，跳过: {}", id, entity.getStatus(), e.getMessage());
          throw e;
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
    // 查询 PENDING_SHIP / SHIPPED 状态的订单供打印列表使用
    // 可选过滤：printType（订单是否已用此类型打印过）
    //   - 入参 null / 空：返回全部待打印订单（不过滤）
    //   - 入参非空：按"曾用此类型打印"过滤（EXISTS 子查询）
    // 前端 controller 已对 'order' 兜底值归一为 null，这里 service 只需判断 null/inject。
    //   加白名单校验是 controller 层职责（防御脏数据落库）。
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name(), OrderStatusEnum.SHIPPED.name());
    if (printType != null && !printType.isBlank()) {
      // 用 SQL 子查询过滤：用 EXISTS 比 JOIN 更简洁，且不重复行
      wrapper.and(w -> w.exists("SELECT 1 FROM mo_order_print_log pl WHERE pl.order_id = mo_order.id AND pl.print_type = {0}", printType));
    }
    wrapper.orderByDesc(OrderEntity::getCreateTime);

    Page<OrderEntity> entityPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

    // 兼容历史订单：mo_order.receiver_* 快照缺失时按 addressId 回查 mo_address 回填
    // （详见 OrderService.fillAddressIfAbsent，避免打印列表收件人/电话/地址显示为空）
    for (OrderEntity e : entityPage.getRecords()) {
      orderService.fillAddressIfAbsent(e);
    }

    // 关键修复：原实现在循环里每条订单都 SUM 一次，是 N+1 查询。
    // 改为一次性 SUM 整页订单 + 内存里 join，1 次 SQL 即可。
    Map<Long, Long> printCountMap = batchSumPrintCounts(
        entityPage.getRecords().stream().map(OrderEntity::getId).collect(Collectors.toList()),
        printType);

    // 关键修复：一次性查出本批订单的所有订单项，避免循环里每条都查一次（与 printCount 同思路）。
    Map<Long, List<com.moyuyo.dao.entity.OrderItemEntity>> itemsByOrderId = batchLoadItems(
        entityPage.getRecords().stream().map(OrderEntity::getId).collect(Collectors.toList()));

    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("orderNo", e.getOrderNo());
      // P0：联系人三件套（收件人 / 电话 / 地址）+ 买家备注
      item.put("receiverName", e.getReceiverName());
      item.put("receiverPhone", e.getReceiverPhone());
      item.put("receiverAddress", e.getReceiverAddress());
      // P0：买家备注（打单前必看，避免漏掉"易碎"/"发顺丰"等关键信息）
      item.put("remark", e.getRemark());
      // P0：实付金额（核对金额、对账必用）
      item.put("payAmount", e.getPayAmount());
      // P1：物流信息（看是否有现成运单号，避免重复打单/重复发货）
      item.put("shippingCarrier", e.getShippingCarrier());
      item.put("trackingNumber", e.getTrackingNumber());
      // P1：订单状态（异常件要在打单前过滤：退款中/已拦截等）
      item.put("status", e.getStatus());
      item.put("statusLabel", orderStatusName(e.getStatus()));
      // P2：运费 / 货币（多币种场景下显示币种便于核对）
      item.put("freight", e.getFreight());
      item.put("currency", e.getCurrency());
      // 查询"打印次数"：用 SUM(print_count) 求和而非行数。批量查询已在循环外完成。
      long printCount = printCountMap.getOrDefault(e.getId(), 0L);
      item.put("printCount", printCount);
      item.put("printStatus", printCount > 0 ? "已打印" : "未打印");
      // P0：商品信息 —— 同时返回结构化 items（用于表格拆列）与拼接文本 productInfo（兼容旧渲染）
      List<com.moyuyo.dao.entity.OrderItemEntity> items = itemsByOrderId.getOrDefault(e.getId(), java.util.Collections.emptyList());
      if (!items.isEmpty()) {
        // 结构化明细：name / skuSpec / quantity / price / skuCode / mainImage
        List<Map<String, Object>> itemList = new ArrayList<>(items.size());
        for (com.moyuyo.dao.entity.OrderItemEntity oi : items) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("productName", oi.getProductName());
          m.put("skuSpec", oi.getSkuSpec());
          m.put("quantity", oi.getQuantity());
          m.put("price", oi.getPrice());
          // P2：SKU 商家编码（与电商后台"商家 SKU"对齐）
          m.put("skuCode", oi.getSkuCode());
          itemList.add(m);
        }
        item.put("items", itemList);
        // 拼接文本保留：用于兼容"未改造的打印内容区"和工具提示
        item.put("productInfo", items.stream()
            .map(oi -> (oi.getProductName() != null ? oi.getProductName() : "") + " x" + oi.getQuantity())
            .collect(Collectors.joining("; ")));
      } else {
        item.put("items", java.util.Collections.emptyList());
        item.put("productInfo", "");
      }
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  @Transactional
  public void recordPrint(Long orderId, String printType, String templateName, String paperSize, String operator) {
    String pt = printType != null ? printType : "PICK";
    String tn = templateName != null ? templateName : "默认模板";
    // 关键修复：paperSize 必须在写入数据库前归一化到"模板表 paper_size 列已知的字典"，
    // 否则 (order_id, paper_size, ...) 维度会出现 'a4' 与 'A4' 两条独立记录、
    // print_count 无法合并累加。
    // 注意：不能简单 toUpperCase() —— 'thermal-100' 含连字符，toUpperCase() 会变成 'THERMAL-100'
    // 与模板表里的小写 'thermal-100' 不一致，所以用白名单映射。
    String ps = normalizePaperSize(paperSize);
    String op = operator != null ? operator : "系统";
    // 查询订单号（即使查不到也要记录日志，避免异常向上抛出影响主流程）
    OrderEntity order = orderMapper.selectById(orderId);
    String orderNo = order != null ? order.getOrderNo() : "";

    // 关键修复 1：原实现每次都 insert 一条新记录，printCount 恒为 1，前端"打印次数"显示永远不正确。
    // 改为按 (orderId, printType, templateName, paperSize) 维度做累加。
    // 关键修复 2：MyBatis-Plus 的 set(field, value) 会拼成 "SET field = <value>"（常量赋值），
    //   不会用"读-改-写"模式 —— 在并发场景下两个线程都读到 printCount=N、各自 N+1 后写入，
    //   结果仍是 N+1，丢失一次计数。因此改用自定义 SQL：SET print_count = print_count + 1，
    //   由数据库原子累加，不存在读-改-写竞态。
    String upsertSql =
        "INSERT INTO mo_order_print_log " +
        "(order_id, order_no, print_type, template_name, paper_size, operator, print_count, create_time, update_time) " +
        "VALUES (?, ?, ?, ?, ?, ?, 1, NOW(), NOW()) " +
        "ON DUPLICATE KEY UPDATE " +
        "  print_count = print_count + 1, " +
        "  update_time = NOW()";
    jdbcTemplate.update(upsertSql, orderId, orderNo, pt, tn, ps, op);
  }

  @Override
  public List<Map<String, Object>> listPrintTemplates() {
    List<PrintTemplateEntity> entities = printTemplateMapper.selectList(
        new LambdaQueryWrapper<PrintTemplateEntity>()
            .orderByAsc(PrintTemplateEntity::getSortOrder)
            .orderByAsc(PrintTemplateEntity::getId));
    List<Map<String, Object>> result = new ArrayList<>(entities.size());
    for (PrintTemplateEntity e : entities) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.getId());
      m.put("code", e.getCode());
      m.put("name", e.getName());
      m.put("paperSize", e.getPaperSize());
      m.put("description", e.getDescription());
      m.put("isDefault", Boolean.TRUE.equals(e.getIsDefault()));
      m.put("sortOrder", e.getSortOrder());
      result.add(m);
    }
    return result;
  }

  @Override
  @Transactional
  public void updatePrintTemplate(Long id, String name, String paperSize, String description,
                                  Boolean isDefault, Integer sortOrder) {
    PrintTemplateEntity entity = printTemplateMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("打印模板不存在: " + id);
    }
    if (name != null && !name.isBlank()) entity.setName(name.trim());
    if (paperSize != null && !paperSize.isBlank()) entity.setPaperSize(paperSize.trim());
    if (description != null) entity.setDescription(description);
    if (sortOrder != null) entity.setSortOrder(sortOrder);
    if (Boolean.TRUE.equals(isDefault)) {
      // 设为默认时，先把其他模板的 isDefault 清掉（保证唯一默认）
      printTemplateMapper.update(null,
          new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrintTemplateEntity>()
              .ne(PrintTemplateEntity::getId, id)
              .set(PrintTemplateEntity::getIsDefault, false));
      entity.setIsDefault(true);
    } else if (isDefault != null) {
      entity.setIsDefault(false);
    }
    entity.setUpdateTime(LocalDateTime.now());
    printTemplateMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void setDefaultPrintTemplate(Long id) {
    PrintTemplateEntity entity = printTemplateMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("打印模板不存在: " + id);
    }
    // 先清掉所有默认标记，再设置新默认
    printTemplateMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrintTemplateEntity>()
            .set(PrintTemplateEntity::getIsDefault, false));
    entity.setIsDefault(true);
    entity.setUpdateTime(LocalDateTime.now());
    printTemplateMapper.updateById(entity);
  }

  @Override
  public Map<String, Object> getPrintDetail(Long orderId) {
    if (orderId == null) return null;
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) return null;
    // 兼容历史订单：mo_order.receiver_* 快照缺失时按 addressId 回查 mo_address 回填
    orderService.fillAddressIfAbsent(order);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", order.getId());
    detail.put("orderNo", order.getOrderNo());
    detail.put("status", order.getStatus());
    detail.put("statusLabel", orderStatusName(order.getStatus()));
    detail.put("payAmount", order.getPayAmount());
    detail.put("receiverName", order.getReceiverName());
    detail.put("receiverPhone", order.getReceiverPhone());
    detail.put("receiverAddress", order.getReceiverAddress());
    detail.put("shippingCarrier", order.getShippingCarrier());
    detail.put("trackingNumber", order.getTrackingNumber());
    detail.put("remark", order.getRemark());
    detail.put("createTime", order.getCreateTime());
    // 订单商品明细（用于打印配货单 / 拣货单）
    List<OrderItemEntity> items = orderItemMapper.selectByOrderId(orderId);
    List<Map<String, Object>> itemList = new ArrayList<>();
    if (items != null) {
      for (OrderItemEntity it : items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productName", it.getProductName());
        m.put("skuSpec", it.getSkuSpec());
        m.put("quantity", it.getQuantity());
        m.put("price", it.getPrice());
        itemList.add(m);
      }
    }
    detail.put("items", itemList);
    return detail;
  }

  @Override
  public YanWenLabelResponse fetchShippingLabel(Long orderId, Long carrierId) {
    if (orderId == null) {
      throw new IllegalArgumentException("orderId 不能为空");
    }
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    // 关键：去掉 @Transactional！
    // 燕文 HTTP 调用可能耗时 1~15s，如果包在事务里会一直占用数据库连接，
    // 高并发时会撑爆连接池（hikari 默认 10 个连接，全卡住后整个服务不可用）。
    // 关键设计变更：fetchShippingLabel 只负责"取面单"，不再累加打印日志。
    // 累加日志改为调用方（前端打印按钮）显式触发 recordShippingLabelLog(orderId)，
    // 避免"预览一次就被记一次打印"的虚假累加。
    return yanWenLabelService.fetchLabel(order, carrierId);
  }

  @Override
  public void recordShippingLabelLog(Long orderId) {
    if (orderId == null) return;
    // 由前端在确认打印（成功调 fetchShippingLabel 后调用 shipOrder / window.print()）之后调用。
    // 写日志失败不抛异常给上层（不影响主流程），但记 warn 日志以便排查。
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      log.warn("recordShippingLabelLog: 订单不存在 orderId={}", orderId);
      return;
    }
    try {
      printLogWriter.recordShippingLabelLog(order);
    } catch (Exception e) {
      log.warn("记录燕文面单打印日志失败：orderId={}, err={}", orderId, e.getMessage());
    }
  }

  @Override
  public boolean isYanwenEnabled() {
    return yanWenLabelService != null && yanWenLabelService.isEnabled();
  }

  /**
   * P0（路径B）：透传到 YanWenLabelService.createWaybill。
   * <p>
   * 注意：service 内部已经把运单号写回订单，这里仅做 controller ↔ service 的薄封装。
   */
  @Override
  public String createYanwenWaybill(Long orderId, Long carrierId) {
    if (orderId == null) {
      throw new IllegalArgumentException("orderId 不能为空");
    }
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    return yanWenLabelService.createWaybill(order, carrierId);
  }

  // ==================== 打印设置（服务端持久化） ====================
  // 复用 mo_system_config（key/value 结构），无需新建表。
  // 注意：所有 admin 共享同一份设置 —— 当前需求是"每个浏览器一份"，
  //   即"运营小张换电脑后设置还在"。若后续需要"每个 admin 单独一份"，
  //   把 key 改成 'order_print_settings:' + userId 即可。

  private static final String PRINT_SETTINGS_KEY = "order_print_settings";

  // 时间轴返回上限：单订单最多返回多少条事件。防止批量打印（每次一条）
  // 或异常订单（被频繁改价/拦截）撑大响应体。超出时按时间升序截断。
  // 可通过 moyuyo.order-ops.timeline-limit 配置（prod 默认 200）。
  // 未来扩展：分页（page/size）+ 游标分页。
  @Value("${moyuyo.order-ops.timeline-limit:200}")
  private int timelineLimit;

  @Override
  public Map<String, Object> getPrintSettings() {
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("paperSize", "a4");
    defaults.put("copies", 1);
    defaults.put("orientation", "portrait");
    defaults.put("duplex", false);
    defaults.put("marginY", 5);
    defaults.put("marginX", 5);
    try {
      // 从 mo_system_config 读 JSON 字符串（config_value 字段为 TEXT）
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT config_value FROM mo_system_config WHERE config_key = ? LIMIT 1",
          PRINT_SETTINGS_KEY);
      if (rows.isEmpty()) return defaults;
      Object raw = rows.get(0).get("config_value");
      if (raw == null) return defaults;
      // 解析 JSON 字符串为 Map（用 Jackson）。Jackson 反序列化 Map.class 是泛型擦除，
      //   转 Map<String,Object> 需要 unchecked 转换；这里数据可信（来自 mo_print_settings.config_value），
      //   与项目里 OrderTimeoutConsumer 等同类写法一致，加 @SuppressWarnings("unchecked") 压制 JDT 误报。
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = objectMapper.readValue(raw.toString(), Map.class);
      defaults.putAll(parsed);
      // P2：类型规范化 —— 防止历史脏数据 / 前端 type coercion 误差导致
      //   v-model 类型不匹配报警。例如 DB 里 "marginY":"5" (字符串) 会让
      //   el-input-number 报 "Expected Number, got String"。
      // 这里把每个字段强制收敛到期望类型：
      //   - paperSize / orientation：String
      //   - copies / marginY / marginX：Integer
      //   - duplex：Boolean
      return normalizePrintSettings(defaults);
    } catch (Exception e) {
      log.warn("读取打印设置失败，返回默认值：{}", e.getMessage());
      return defaults;
    }
  }

  /**
   * 将读取到的 Map 规范化到前端期望的类型。
   * 不识别的值（如 null / 字符串非数字）回退到默认值。
   */
  private static Map<String, Object> normalizePrintSettings(Map<String, Object> raw) {
    Map<String, Object> out = new LinkedHashMap<>(raw);
    // paperSize：白名单字符串
    Object paperSize = raw.get("paperSize");
    if (paperSize instanceof String s && !s.isBlank()) {
      out.put("paperSize", s);
    } else {
      out.put("paperSize", "a4");
    }
    // orientation：白名单字符串
    Object orientation = raw.get("orientation");
    if (orientation instanceof String s && !s.isBlank()) {
      out.put("orientation", s);
    } else {
      out.put("orientation", "portrait");
    }
    // copies / marginY / marginX：Integer（>=0）
    out.put("copies", toNonNegativeInt(raw.get("copies"), 1));
    out.put("marginY", toNonNegativeInt(raw.get("marginY"), 5));
    out.put("marginX", toNonNegativeInt(raw.get("marginX"), 5));
    // duplex：Boolean
    out.put("duplex", toBoolean(raw.get("duplex")));
    return out;
  }

  private static int toNonNegativeInt(Object v, int defaultVal) {
    if (v == null) return defaultVal;
    if (v instanceof Number n) return Math.max(0, n.intValue());
    if (v instanceof String s) {
      try {
        return Math.max(0, Integer.parseInt(s.trim()));
      } catch (NumberFormatException ignored) {
        return defaultVal;
      }
    }
    return defaultVal;
  }

  private static boolean toBoolean(Object v) {
    if (v == null) return false;
    if (v instanceof Boolean b) return b;
    if (v instanceof Number n) return n.intValue() != 0;
    if (v instanceof String s) {
      // 兼容 "true"/"false"/"1"/"0"/"yes"/"no"
      String t = s.trim().toLowerCase();
      if (t.equals("true") || t.equals("1") || t.equals("yes")) return true;
      if (t.equals("false") || t.equals("0") || t.equals("no")) return false;
    }
    return false;
  }

  @Override
  @Transactional
  public void savePrintSettings(Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      throw new IllegalArgumentException("打印设置不能为空");
    }
    try {
      String json = objectMapper.writeValueAsString(body);
      // mo_system_config.id 是手填 BIGINT（非自增），并发同毫秒 INSERT 会冲突。
      // 先用唯一索引 (config_key) + UPDATE 走幂等路径，仅在未初始化时才走 INSERT。
      // 这样在 V20260929_06 已预置默认行的情况下，并发保存是 UPDATE + 唯一键保护，
      // 不存在手填 id 冲突。
      int affected = jdbcTemplate.update(
          "UPDATE mo_system_config SET config_value = ?, update_time = NOW() WHERE config_key = ?",
          json,
          PRINT_SETTINGS_KEY);
      if (affected == 0) {
        // 行不存在（极端：migration 没跑成功）→ 用 INSERT 兜底，使用 MAX(id)+1 避免冲突
        jdbcTemplate.update(
            "INSERT INTO mo_system_config (id, config_key, config_value, remark, create_time, update_time) " +
            "VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM mo_system_config), ?, ?, ?, NOW(), NOW())",
            PRINT_SETTINGS_KEY,
            json,
            "OrderPrint 页面默认打印设置（纸张/份数/方向/边距）");
      }
      log.info("保存打印设置成功，affected={}, json={}", affected, json);
    } catch (Exception e) {
      log.error("保存打印设置失败：", e);
      throw new RuntimeException("保存打印设置失败：" + e.getMessage(), e);
    }
  }

  /**
   * 批量 SUM 打印次数。
   * <p>
   * 替代逐订单查询的 N+1 模式：1 次 SQL 算出所有 orderId 的 SUM(print_count)。
   * <p>
   * printType 为空时按订单 sum 所有 print_type；否则只 sum 该 print_type 维度。
   */

  /**
   * 批量加载订单项：按 orderId 一次性查出，避免循环 N+1。
   * 与 batchSumPrintCounts 思路一致：1 次 SQL 拉回本页所有订单的商品明细，内存里 join。
   */
  private Map<Long, List<com.moyuyo.dao.entity.OrderItemEntity>> batchLoadItems(List<Long> orderIds) {
    if (orderIds == null || orderIds.isEmpty()) return new HashMap<>();
    // IN 子查询占位符（?, ?, ..., ?）
    StringBuilder inClause = new StringBuilder();
    for (int i = 0; i < orderIds.size(); i++) {
      if (i > 0) inClause.append(", ");
      inClause.append("?");
    }
    // 关键：OrderItemEntity.skuCode 是 @TableField(exist = false) 的非表字段，
    //   MyBatis-Plus 默认会忽略。这里改用 jdbcTemplate.queryForList + 手写映射，
    //   只取实际表字段，避免触发 MyBatis 对非表字段的"列不存在"异常。
    String sql = "SELECT id, order_id, product_id, sku_id, product_name, sku_spec, " +
                 "       main_image, price, quantity, subtotal, create_time " +
                 "FROM mo_order_item WHERE order_id IN (" + inClause + ")";
    Map<Long, List<com.moyuyo.dao.entity.OrderItemEntity>> result = new HashMap<>();
    jdbcTemplate.query(sql, rs -> {
      long oid = rs.getLong("order_id");
      com.moyuyo.dao.entity.OrderItemEntity it = new com.moyuyo.dao.entity.OrderItemEntity();
      it.setId(rs.getLong("id"));
      it.setOrderId(oid);
      it.setProductId(rs.getLong("product_id"));
      it.setSkuId(rs.getLong("sku_id"));
      it.setProductName(rs.getString("product_name"));
      it.setSkuSpec(rs.getString("sku_spec"));
      it.setMainImage(rs.getString("main_image"));
      java.math.BigDecimal price = rs.getBigDecimal("price");
      it.setPrice(price);
      it.setQuantity(rs.getInt("quantity"));
      java.math.BigDecimal subtotal = rs.getBigDecimal("subtotal");
      it.setSubtotal(subtotal);
      java.sql.Timestamp ts = rs.getTimestamp("create_time");
      if (ts != null) it.setCreateTime(ts.toLocalDateTime());
      // skuCode 是非表字段（@TableField(exist=false)），这里不强求赋值；
      //   若业务需要商家编码，应改为从 mo_product_sku 表关联读取。
      result.computeIfAbsent(oid, k -> new ArrayList<>()).add(it);
    }, orderIds.toArray());
    return result;
  }

  private Map<Long, Long> batchSumPrintCounts(List<Long> orderIds, String printType) {
    if (orderIds == null || orderIds.isEmpty()) return new HashMap<>();
    // IN 子查询占位符（?, ?, ..., ?）
    StringBuilder inClause = new StringBuilder();
    for (int i = 0; i < orderIds.size(); i++) {
      if (i > 0) inClause.append(", ");
      inClause.append("?");
    }
    String sql;
    Object[] args;
    if (printType != null && !printType.isEmpty()) {
      sql = "SELECT order_id, COALESCE(SUM(print_count), 0) AS cnt " +
            "FROM mo_order_print_log " +
            "WHERE order_id IN (" + inClause + ") AND print_type = ? " +
            "GROUP BY order_id";
      args = new Object[orderIds.size() + 1];
      for (int i = 0; i < orderIds.size(); i++) args[i] = orderIds.get(i);
      args[orderIds.size()] = printType;
    } else {
      sql = "SELECT order_id, COALESCE(SUM(print_count), 0) AS cnt " +
            "FROM mo_order_print_log " +
            "WHERE order_id IN (" + inClause + ") " +
            "GROUP BY order_id";
      args = orderIds.toArray();
    }
    Map<Long, Long> result = new HashMap<>();
    jdbcTemplate.query(sql, rs -> {
      result.put(rs.getLong("order_id"), rs.getLong("cnt"));
    }, args);
    return result;
  }

  /**
   * 纸规格字典归一化：把前端各种大小写/拼写变体映射到模板表 mo_print_template.paper_size 的官方值。
   * <p>
   * 与 V20260929_03 迁移脚本保持一致：
   *   'a4' / 'A4' → 'A4'
   *   'a5' / 'A5' → 'A5'
   *   'thermal-80' / 'THERMAL-80' / 'thermal 80' → 'THERMAL-80'（按现有数据，但建议入库存 'thermal-80' 小写）
   *   'thermal-100' / 'THERMAL-100' → 'thermal-100'
   * <p>
   * 注意：模板表里 'thermal-100' 是小写（迁移脚本第27行），为保持兼容热敏纸拼写采用小写。
   * 不能简单 toUpperCase() —— 那会把 'thermal-100' 变成 'THERMAL-100'，与模板不一致。
   */
  private static String normalizePaperSize(String raw) {
    if (raw == null) return "A4";
    String s = raw.trim();
    switch (s.toLowerCase()) {
      case "a4": return "A4";
      case "a5": return "A5";
      case "thermal-80":
      case "thermal 80":
      case "thermal80": return "THERMAL-80";
      case "thermal-100":
      case "thermal 100":
      case "thermal100": return "thermal-100";
      default: return s; // 兜底：原样写库，让运维从数据库侧修复
    }
  }

  /** 订单状态枚举 → 中文名（与前端 statusLabel 保持一致） */
  private static String orderStatusName(String status) {
    if (status == null) return "未知";
    return switch (status.toUpperCase()) {
      case "PENDING_PAY" -> "待付款";
      case "PAID" -> "已支付";
      case "PENDING_SHIP" -> "待发货";
      case "SHIPPED" -> "已发货";
      case "RECEIVED" -> "已收货";
      case "COMPLETED" -> "已完成";
      case "CANCELLED", "CANCELED" -> "已取消";
      case "REFUNDING" -> "退款中";
      case "REFUNDED" -> "已退款";
      case "EXCHANGING" -> "换货中";
      case "EXCHANGED" -> "已换货";
      case "HOLD" -> "已拦截";
      default -> status;
    };
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

    // 更新订单状态为已拦截（使用 HOLD 中间态）
    order.setStatus(OrderStatusEnum.HOLD.name());
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

  // ==================== 订单时间轴 ====================
  // 设计思路 —— 用 JdbcTemplate 一次性读 5 张表的事件，按时间合并排序后返回。
  //   1) mo_order：本体事件（创建 / 支付 / 发货 / 收货）
  //   2) mo_logistics：物流节点（出货 / 签收）
  //   3) mo_order_intercept：拦截与解除（ACTIVE/RELEASED 双向）
  //   4) mo_order_price_modify：改价
  //   5) mo_order_print_log：打印
  // 每个事件为 { time, type, title, detail }，前端按 time 升序渲染。
  // 实现要点：
  //   - 单订单聚合，每张表按 order_id 索引过滤，行数极少（通常 < 10 条），无性能压力。
  //   - 订单不存在 → 返回空列表，由 controller 层 404。
  //   - SQL 直接 JdbcTemplate，绕过 MyBatis-Plus 实体映射（避免引入更多 mapper）。

  @Override
  public Map<String, Object> getOrderTimeline(Long orderId, Integer limit, Integer offset) {
    // 返回包装：{ events, total, limit, offset }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("events", new ArrayList<Map<String, Object>>());
    result.put("total", 0);
    result.put("limit", timelineLimit);
    result.put("offset", 0);
    if (orderId == null) return result;
    // 参数兜底：limit 取 @Value 注入的配置（默认 200），offset 默认 0
    int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, timelineLimit) : timelineLimit;
    // offset 上限 1,000,000：单订单事件数实际 < 1000，超过就拒绝任务（前端不必无限重试）
    int effectiveOffset = offset != null && offset >= 0 ? Math.min(offset, 1_000_000) : 0;
    result.put("limit", effectiveLimit);
    result.put("offset", effectiveOffset);
    // result.get 返回 Object；本方法构造 result 时已保证 events 是 List<Map<String,Object>>
    // （调用方均为本类其他业务方法），与项目里同类写法一致，加 @SuppressWarnings("unchecked") 压制 JDT 误报
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");

    try {
      // 性能优化：用 UNION ALL 把 5 张表的查询合并为一次 DB 往返，
      //   避免 5 次独立往返（每次都要建连 / 释放）。
      //   每条事件统一为 (event_time, event_type, event_title, event_detail) 4 列，
      //   上层代码只需按 event_time 排序即可。
      //
      // 注意：UNION ALL 不同表的时间字段命名不一致（create_time / paid_at / shipped_at ...），
      //   这里统一重命名为 type + time + detail（驼峰，与前端 JS 命名约定一致）。
      //   前端 getOrderTimeline 期望 { type, time, detail }。
      String sql =
              "SELECT * FROM (" +
              "SELECT 'ORDER_CREATED' AS type, create_time AS time, " +
              "       CONCAT('订单号：', order_no) AS detail " +
              "FROM mo_order WHERE id = ? AND create_time IS NOT NULL " +
              "UNION ALL " +
              "SELECT 'ORDER_PAID', paid_at, NULL FROM mo_order " +
              "WHERE id = ? AND paid_at IS NOT NULL " +
              "UNION ALL " +
              "SELECT 'ORDER_SHIPPED', deliver_time, NULL FROM mo_order " +
              "WHERE id = ? AND deliver_time IS NOT NULL " +
              "UNION ALL " +
              "SELECT 'ORDER_RECEIVED', received_time, NULL FROM mo_order " +
              "WHERE id = ? AND received_time IS NOT NULL " +
              "UNION ALL " +
              // LOGISTICS_SHIPPED：carrier + tracking_number 用 NULLIF+TRIM 把空串变 NULL，
              //   CONCAT_WS 自动跳过 NULL，避免出现前导/尾随空格或单空格脏数据。
              "SELECT 'LOGISTICS_SHIPPED', shipped_at, " +
              "       CONCAT_WS(' ', NULLIF(TRIM(carrier), ''), NULLIF(TRIM(tracking_number), '')) " +
              "FROM mo_logistics WHERE order_id = ? AND shipped_at IS NOT NULL " +
              "UNION ALL " +
              "SELECT 'LOGISTICS_RECEIVED', received_at, NULL " +
              "FROM mo_logistics WHERE order_id = ? AND received_at IS NOT NULL " +
              "UNION ALL " +
              // INTERCEPT_ACTIVE：拦截类型默认值 MANUAL；reason 可空（NULLIF+TRIM 把空串也视作 NULL 让 CONCAT_WS 跳过）
              "SELECT 'INTERCEPT_ACTIVE', create_time, " +
              "       CONCAT_WS('；', " +
              "              CONCAT('拦截类型：', IFNULL(NULLIF(TRIM(intercept_type),''), 'MANUAL')), " +
              "              CONCAT('原因：', NULLIF(TRIM(reason), ''))) " +
              "FROM mo_order_intercept WHERE order_id = ? " +
              "UNION ALL " +
              // INTERCEPT_RELEASED：release_operator 可空，默认值 "(未填写)" 占位
              "SELECT 'INTERCEPT_RELEASED', release_time, " +
              "       CONCAT('解除人：', IFNULL(NULLIF(TRIM(release_operator),''), '(未填写)')) " +
              "FROM mo_order_intercept WHERE order_id = ? AND release_time IS NOT NULL " +
              "UNION ALL " +
              // PRICE_MODIFIED：amounts 必有值；reason / operator 可空
              "SELECT 'PRICE_MODIFIED', create_time, " +
              "       CONCAT_WS('；', " +
              "              CONCAT('调整 ', IFNULL(adjust_amount,'0'), ' 元（原 ', " +
              "                       IFNULL(original_amount,'0'), ' → 现 ', " +
              "                       IFNULL(final_amount,'0'), '）'), " +
              "              CONCAT('原因：', NULLIF(TRIM(reason), '')), " +
              "              CONCAT('操作人：', NULLIF(TRIM(operator), ''))) " +
              "FROM mo_order_price_modify WHERE order_id = ? " +
              "UNION ALL " +
              // PRINT_RECORD：模板/纸张/打印类型都有默认值（迁移 DEFAULT 'PICK'/'默认'/'A4'）
              "SELECT 'PRINT_RECORD', create_time, " +
              "       CONCAT_WS('；', " +
              "              CONCAT('打印 ', IFNULL(NULLIF(TRIM(print_type),''), 'PICK')), " +
              "              CONCAT('模板：', IFNULL(NULLIF(TRIM(template_name),''), '默认')), " +
              "              CONCAT('纸张：', IFNULL(NULLIF(TRIM(paper_size),''), 'A4')), " +
              "              CONCAT('操作人：', IFNULL(NULLIF(TRIM(operator),''), '(未填写)')) " +
              "FROM mo_order_print_log WHERE order_id = ? " +
              ") AS events " +
              "ORDER BY time ASC " +
              "LIMIT ? OFFSET ?";
      // 12 个参数占位：10 个 orderId（依次出现位置）+ 2 个 LIMIT / OFFSET
      Object[] args = new Object[12];
      java.util.Arrays.fill(args, orderId);
      // 最后两位：LIMIT, OFFSET
      args[10] = Math.max(1, effectiveLimit);
      args[11] = effectiveOffset;

      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
      if (!rows.isEmpty()) {
        for (Map<String, Object> r : rows) {
        String type = (String) r.get("type");
        Object time = r.get("time");
        Object detail = r.get("detail");
        // 业务字段映射到中文 title
        String title = switch (type) {
          case "ORDER_CREATED" -> "订单创建";
          case "ORDER_PAID" -> "订单已支付";
          case "ORDER_SHIPPED" -> "订单已发货";
          case "ORDER_RECEIVED" -> "订单已收货";
          case "LOGISTICS_SHIPPED" -> "物流已揽件";
          case "LOGISTICS_RECEIVED" -> "物流已签收";
          case "INTERCEPT_ACTIVE" -> "订单已被拦截";
          case "INTERCEPT_RELEASED" -> "拦截已解除";
          case "PRICE_MODIFIED" -> "订单改价";
          case "PRINT_RECORD" -> "打印记录";
          default -> type;
        };
        addEvent(events, time, type, title, detail != null ? detail.toString() : null);
        }
      }
      // 计算 total：去掉 LIMIT/OFFSET 再跑一遍 SQL 数总事件数。
      // 性能考虑：单订单事件数 < 1000，每张表按 order_id 索引过滤，10 个 COUNT 子查询 O(10)ms 级。
      // 比起 UNION ALL 的 LIMIT 计算，整体开销可接受。
      // 短路：events 为空则不必再算 total（订单不存在时 controller 直接返 404）
      int total = events.isEmpty() ? 0 : countOrderTimeline(orderId);
      result.put("total", total);
      return result;
    } catch (Exception e) {
      log.warn("聚合订单时间轴失败：orderId={}, err={}", orderId, e.getMessage());
      return result;
    }
  }

  /**
   * 统计订单时间轴的总事件数（不带 LIMIT/OFFSET）。
   * 与 getOrderTimeline 同样的 5 张表 UNION ALL，只是不带时间细节，列固定为常数。
   * 用 SUM(...) 而非 COUNT(*) OVER()：后者 MySQL 8.0+ 支持但需要窗口排序，复杂度反而高。
   */
  private int countOrderTimeline(Long orderId) {
    try {
      String sql =
          "SELECT " +
          "  (SELECT COUNT(*) FROM mo_order WHERE id = ? AND create_time IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_order WHERE id = ? AND paid_at IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_order WHERE id = ? AND deliver_time IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_order WHERE id = ? AND received_time IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_logistics WHERE order_id = ? AND shipped_at IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_logistics WHERE order_id = ? AND received_at IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_order_intercept WHERE order_id = ?) + " +
          "  (SELECT COUNT(*) FROM mo_order_intercept WHERE order_id = ? AND release_time IS NOT NULL) + " +
          "  (SELECT COUNT(*) FROM mo_order_price_modify WHERE order_id = ?) + " +
          "  (SELECT COUNT(*) FROM mo_order_print_log WHERE order_id = ?)";
      Integer cnt = jdbcTemplate.queryForObject(sql, Integer.class,
          orderId, orderId, orderId, orderId, orderId, orderId, orderId, orderId, orderId, orderId);
      return cnt != null ? cnt : 0;
    } catch (Exception e) {
      log.warn("统计订单时间轴事件数失败：orderId={}, err={}", orderId, e.getMessage());
      return 0;
    }
  }

  /** 时间轴事件构造工具：把任意时间类型归一为 ISO 字符串或原样保留 Temporal */
  private static void addEvent(List<Map<String, Object>> events, Object time,
                                String type, String title, String detail) {
    if (time == null) return;
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("time", time);
    e.put("type", type);
    e.put("title", title);
    e.put("detail", detail);
    events.add(e);
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
      // 全部异常：HOLD（被运营拦截）+ CANCELLED（已取消，已包含退款类目）
      // 注意：用 OrderStatusEnum.HOLD.name() 而非字面量 "HOLD"，避免枚举改名时脱节。
      LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
      wrapper.in(OrderEntity::getStatus, OrderStatusEnum.HOLD.name(), OrderStatusEnum.CANCELLED.name())
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
