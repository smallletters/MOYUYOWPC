package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.*;
import com.moyuyo.dao.admin.mapper.*;
import com.moyuyo.dao.admin.entity.InventoryEntity;
import com.moyuyo.dao.admin.entity.InventoryTransferEntity;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.admin.AdminShippingStrategyService;
import com.moyuyo.service.admin.AdminShippingZoneService;
import com.moyuyo.common.dto.admin.logistics.CarrierCreateRequest;
import com.moyuyo.common.dto.admin.logistics.CarrierUpdateRequest;
import com.moyuyo.common.dto.admin.logistics.ClearanceCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ClearanceUpdateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingStrategyUpdateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingZoneUpdateRequest;
import com.moyuyo.service.admin.AdminCarrierService;
import com.moyuyo.service.admin.AdminClearanceService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Tag(name = "管理后台 - 物流管理")
@RestController
@RequestMapping("/api/admin/logistics")
@SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
public class AdminLogisticsController {

  private final LogisticsMapper logisticsMapper;
  private final OrderMapper orderMapper;
  private final WarehouseMapper warehouseMapper;
  private final ClearanceMapper clearanceMapper;
  private final AdminShippingZoneService adminShippingZoneService;
  private final AdminShippingStrategyService adminShippingStrategyService;
  private final AdminCarrierService adminCarrierService;
  private final AdminClearanceService adminClearanceService;
  private final MergePackageMapper mergePackageMapper;
  private final SplitPackageMapper splitPackageMapper;
  private final ProductMapper productMapper;
  private final CategoryMapper categoryMapper;
  private final InventoryTransferMapper inventoryTransferMapper;
  private final InventoryMapper inventoryMapper;
  private final JdbcTemplate jdbcTemplate;

  @Operation(summary = "物流KPI统计")
  @GetMapping("/kpi")
  public Result<Map<String, Object>> kpi() {
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

    Long todayPackages = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>()
            .ge(LogisticsEntity::getShippedAt, todayStart));

    Long pendingPick = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>()
            .isNull(LogisticsEntity::getShippedAt));

    Long inTransit = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>()
            .isNotNull(LogisticsEntity::getShippedAt)
            .isNull(LogisticsEntity::getReceivedAt));

    Long delivered = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>()
            .isNotNull(LogisticsEntity::getReceivedAt));

    List<LogisticsEntity> completedList = logisticsMapper.selectList(
        new LambdaQueryWrapper<LogisticsEntity>()
            .isNotNull(LogisticsEntity::getShippedAt)
            .isNotNull(LogisticsEntity::getReceivedAt));
    double avgDeliveryHours = 0;
    if (!completedList.isEmpty()) {
      avgDeliveryHours = completedList.stream()
          .mapToLong(e -> ChronoUnit.HOURS.between(e.getShippedAt(), e.getReceivedAt()))
          .average()
          .orElse(0);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("todayPackages", todayPackages);
    data.put("pendingPick", pendingPick);
    data.put("inTransit", inTransit);
    data.put("delivered", delivered);
    data.put("abnormal", 0);
    data.put("avgDeliveryHours", Math.round(avgDeliveryHours * 10.0) / 10.0);
    return Result.success(data);
  }

  @Operation(summary = "包裹列表")
  @GetMapping("/packages")
  public Result<Map<String, Object>> packages(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {
    LambdaQueryWrapper<LogisticsEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      if ("PENDING".equals(status)) {
        wrapper.isNull(LogisticsEntity::getShippedAt);
      } else if ("IN_TRANSIT".equals(status)) {
        wrapper.isNotNull(LogisticsEntity::getShippedAt)
            .isNull(LogisticsEntity::getReceivedAt);
      } else if ("DELIVERED".equals(status)) {
        wrapper.isNotNull(LogisticsEntity::getReceivedAt);
      }
    }
    wrapper.orderByDesc(LogisticsEntity::getShippedAt);

    // 使用 MyBatis-Plus Page 进行数据库分页查询
    Page<LogisticsEntity> pageResult = logisticsMapper.selectPage(new Page<>(page, size), wrapper);

    // 一次性批量取出本页所有订单，避免循环内 N+1 查询
    List<LogisticsEntity> records = pageResult.getRecords();
    java.util.Set<Long> orderIds = new java.util.HashSet<>();
    for (LogisticsEntity logi : records) {
      if (logi.getOrderId() != null) orderIds.add(logi.getOrderId());
    }
    Map<Long, OrderEntity> orderMap = new java.util.HashMap<>();
    if (!orderIds.isEmpty()) {
      // selectBatchIds 在 MyBatis-Plus 3.5.x 已废弃，改用 selectByIds（同签名，避免 IDE 黄色感叹号）
      List<OrderEntity> orders = orderMapper.selectByIds(orderIds);
      for (OrderEntity o : orders) {
        if (o != null && o.getId() != null) orderMap.put(o.getId(), o);
      }
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for (LogisticsEntity logi : records) {
      OrderEntity order = orderMap.get(logi.getOrderId());

      String statusStr;
      if (logi.getReceivedAt() != null) {
        statusStr = "DELIVERED";
      } else if (logi.getShippedAt() != null) {
        statusStr = "IN_TRANSIT";
      } else {
        statusStr = "PENDING";
      }

      String origin = "";
      String destination = "";
      if (order != null) {
        if (order.getSenderAddress() != null) {
          origin = extractCity(order.getSenderAddress());
        }
        if (order.getReceiverAddress() != null) {
          destination = extractCity(order.getReceiverAddress());
        }
      }

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", logi.getId());
      item.put("trackingNo", logi.getTrackingNumber() != null ? logi.getTrackingNumber() : "");
      item.put("carrier", logi.getCarrier() != null ? logi.getCarrier() : "");
      item.put("origin", origin);
      item.put("destination", destination);
      item.put("status", statusStr);
      item.put("estimatedDelivery", logi.getShippedAt() != null ? logi.getShippedAt().plusDays(3) : null);
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("records", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  /**
   * 从地址字符串中提取城市名（如"上海市浦东新区" -> "上海市"）
   */
  private String extractCity(String address) {
    if (address == null || address.isEmpty()) {
      return "";
    }
    int cityIndex = address.indexOf("市");
    if (cityIndex > 0) {
      int start = Math.max(address.lastIndexOf("省", cityIndex),
          Math.max(address.lastIndexOf("自治区", cityIndex), 0));
      if (start > 0) {
        if (address.substring(start).startsWith("自治区")) {
          return address.substring(start + 3, cityIndex + 1);
        }
        return address.substring(start + 1, cityIndex + 1);
      }
      return address.substring(0, cityIndex + 1);
    }
    return "";
  }

  @Operation(summary = "仓库列表")
  @GetMapping("/warehouses")
  public Result<List<Map<String, Object>>> warehouses(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      List<WarehouseEntity> records = warehouseMapper.selectList(
          new LambdaQueryWrapper<WarehouseEntity>().orderByDesc(WarehouseEntity::getId));
      List<Map<String, Object>> list = new ArrayList<>();
      for (WarehouseEntity w : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", w.getId());
        item.put("name", w.getName());
        item.put("type", w.getType());
        item.put("city", w.getCity());
        item.put("area", w.getArea());
        item.put("manager", w.getManager());
        item.put("phone", w.getPhone());
        item.put("status", w.getStatus());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("仓库列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "新增仓库")
  @PostMapping("/warehouses")
  public Result<Map<String, Object>> createWarehouse(@RequestBody Map<String, Object> body) {
    WarehouseEntity entity = new WarehouseEntity();
    entity.setName((String) body.get("name"));
    entity.setType((String) body.get("type"));
    entity.setCity((String) body.get("city"));
    entity.setAddress((String) body.get("address"));
    entity.setArea(body.get("area") != null ? Integer.valueOf(body.get("area").toString()) : null);
    entity.setManager((String) body.get("manager"));
    entity.setPhone((String) body.get("phone"));
    entity.setStatus((String) body.get("status"));
    warehouseMapper.insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "仓库创建成功");
    return Result.success(result);
  }

  @Operation(summary = "删除仓库")
  @DeleteMapping("/warehouses/{id}")
  public Result<Map<String, Object>> deleteWarehouse(@PathVariable Long id) {
    warehouseMapper.deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "仓库删除成功");
    return Result.success(result);
  }

  @Operation(summary = "更新仓库")
  @PutMapping("/warehouses/{id}")
  public Result<Map<String, Object>> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    WarehouseEntity entity = new WarehouseEntity();
    entity.setId(id);
    entity.setName((String) body.get("name"));
    entity.setType((String) body.get("type"));
    entity.setCity((String) body.get("city"));
    entity.setAddress((String) body.get("address"));
    entity.setArea(body.get("area") != null ? Integer.valueOf(body.get("area").toString()) : null);
    entity.setManager((String) body.get("manager"));
    entity.setPhone((String) body.get("phone"));
    entity.setStatus((String) body.get("status"));
    warehouseMapper.updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "仓库更新成功");
    return Result.success(result);
  }

  // ==================== 仓库扩展端点（前端 WarehouseManage.vue 使用）====================

  @Operation(summary = "仓库KPI汇总")
  @GetMapping("/warehouse/kpi")
  public Result<Map<String, Object>> warehouseKpi() {
    List<WarehouseEntity> warehouses = warehouseMapper.selectList(
        new LambdaQueryWrapper<WarehouseEntity>().orderByAsc(WarehouseEntity::getId));
    int total = warehouses.size();
    // ACTIVE = 启用
    long active = warehouses.stream()
        .filter(w -> "ACTIVE".equalsIgnoreCase(w.getStatus()))
        .count();

    // 最大容量（来自 mo_warehouse.max_capacity_qty）
    long totalMaxCapacity = warehouses.stream()
        .mapToLong(w -> w.getMaxCapacityQty() == null ? 0L : w.getMaxCapacityQty())
        .sum();
    // 在库件数（来自 mo_inventory.quantity）
    List<InventoryEntity> allInventory = inventoryMapper.selectList(null);
    long totalStockQty = allInventory.stream()
        .mapToLong(inv -> inv.getQuantity() == null ? 0L : inv.getQuantity())
        .sum();
    // 平均仓库利用率：∑库存件数 / ∑最大容量（按仓库层级加权）
    double avgUsage = totalMaxCapacity > 0
        ? Math.round((totalStockQty * 1000.0 / totalMaxCapacity)) / 10.0
        : -1d;
    // 在途件数：按"调拨单 IN_TRANSIT 状态的件数总和"统计（更符合仓库在途语义）
    List<InventoryTransferEntity> inTransitList = inventoryTransferMapper.selectList(
        new LambdaQueryWrapper<InventoryTransferEntity>()
            .eq(InventoryTransferEntity::getStatus, "IN_TRANSIT"));
    long inTransit = inTransitList.stream()
        .mapToLong(t -> t.getQuantity() == null ? 0L : t.getQuantity())
        .sum();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", total);
    data.put("active", active);
    // 最大可容纳库存件数（单位 件）
    data.put("totalCapacity", totalMaxCapacity);
    // 在库件数（单位 件）
    data.put("usedCapacity", totalStockQty);
    // 平均利用率（百分比，-1 表示暂无数据）
    data.put("avgUsage", avgUsage);
    // 在途件数
    data.put("inTransit", inTransit);
    // 额外字段：总 SKU 库存记录数（用于卡片展示）
    data.put("totalSkuRecords", allInventory.size());
    // 单位提示（前端文案兜底）
    data.put("capacityUnit", "件");
    data.put("stockUnit", "件");
    return Result.success(data);
  }

  @Operation(summary = "仓库智能分配建议列表")
  @GetMapping("/warehouse/allocation-suggest")
  public Result<List<Map<String, Object>>> warehouseAllocationSuggest() {
    // mo_warehouse_allocation_suggest 表无对应 Entity，用 JdbcTemplate 读取
    String sql = "SELECT id, product_id, product_name, from_warehouse, to_warehouse, " +
        "qty, reason, priority, status, create_time " +
        "FROM mo_warehouse_allocation_suggest " +
        "ORDER BY priority ASC, create_time DESC";
    List<Map<String, Object>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", rs.getLong("id"));
      item.put("productId", rs.getObject("product_id"));
      item.put("productName", rs.getString("product_name"));
      item.put("fromWarehouse", rs.getString("from_warehouse"));
      item.put("toWarehouse", rs.getString("to_warehouse"));
      item.put("qty", rs.getObject("qty"));
      item.put("reason", rs.getString("reason"));
      item.put("priority", rs.getObject("priority"));
      item.put("status", rs.getString("status"));
      Timestamp ts = rs.getTimestamp("create_time");
      item.put("createTime", ts == null ? null : ts.toLocalDateTime().toString());
      return item;
    });
    return Result.success(list);
  }

  @Operation(summary = "采纳仓库智能分配建议")
  @PostMapping("/warehouse/allocation-suggest/{id}/apply")
  public Result<Map<String, Object>> applyAllocationSuggest(@PathVariable Long id) {
    String sql = "UPDATE mo_warehouse_allocation_suggest SET status = 'APPLIED' WHERE id = ?";
    int rows = jdbcTemplate.update(sql, id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("affected", rows);
    result.put("message", rows > 0 ? "已采纳" : "记录不存在");
    return Result.success(result);
  }

  @Operation(summary = "各仓库存总览（SKU 种类 / 可用库存 / 在途）")
  @GetMapping("/warehouse/stock-overview")
  public Result<List<Map<String, Object>>> warehouseStockOverview() {
    try {
      // 仓库数量少，一次查询即可
      List<WarehouseEntity> warehouses = warehouseMapper.selectList(
          new LambdaQueryWrapper<WarehouseEntity>().orderByAsc(WarehouseEntity::getId));

      // 库存按仓库聚合：一次 SQL 完成 SKU 去重计数与在库件数求和
      // 走 idx_inventory_warehouse(warehouse_id) 索引，避免全表数据加载到内存
      Map<Long, Map<String, Object>> stockByWarehouse = new HashMap<>();
      jdbcTemplate.query(
          "SELECT warehouse_id, COUNT(DISTINCT product_id) AS sku_types, " +
              "COALESCE(SUM(quantity), 0) AS available_stock " +
              "FROM mo_inventory WHERE warehouse_id IS NOT NULL GROUP BY warehouse_id",
          rs -> {
            Map<String, Object> stat = new HashMap<>(2);
            stat.put("skuTypes", rs.getLong("sku_types"));
            stat.put("availableStock", rs.getLong("available_stock"));
            stockByWarehouse.put(rs.getLong("warehouse_id"), stat);
          });

      // 在途按调入仓聚合：仅统计 IN_TRANSIT 记录，走 idx_status 索引
      Map<Long, Long> inTransitByWarehouse = new HashMap<>();
      jdbcTemplate.query(
          "SELECT to_warehouse_id, COALESCE(SUM(quantity), 0) AS in_transit " +
              "FROM mo_inventory_transfer " +
              "WHERE status = 'IN_TRANSIT' AND to_warehouse_id IS NOT NULL " +
              "GROUP BY to_warehouse_id",
          rs -> {
            inTransitByWarehouse.put(rs.getLong("to_warehouse_id"), rs.getLong("in_transit"));
          });

      List<Map<String, Object>> list = new ArrayList<>();
      for (WarehouseEntity w : warehouses) {
        Map<String, Object> stat = stockByWarehouse.get(w.getId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", w.getId());
        item.put("name", w.getName());
        item.put("city", w.getCity());
        item.put("skuTypes", stat == null ? 0L : stat.get("skuTypes"));
        item.put("availableStock", stat == null ? 0L : stat.get("availableStock"));
        item.put("inTransit", inTransitByWarehouse.getOrDefault(w.getId(), 0L));
        list.add(item);
      }
      return Result.success(list);
    } catch (Exception e) {
      // 兜底：表缺失或 SQL 异常时返回业务错误，避免直接 500
      log.error("各仓库存总览查询失败", e);
      return Result.error("各仓库存总览查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "创建入仓计划")
  @PostMapping("/warehouse/inbound-plan")
  public Result<Map<String, Object>> createInboundPlan(@RequestBody Map<String, Object> body) {
    String productName = body.get("productName") == null ? "" : String.valueOf(body.get("productName")).trim();
    if (productName.isEmpty()) {
      return Result.error(400, "参数错误：商品名称不能为空");
    }
    // 兼容 warehouse / toWarehouse 两种字段名
    String warehouse = body.get("warehouse") != null
        ? String.valueOf(body.get("warehouse")).trim()
        : (body.get("toWarehouse") != null ? String.valueOf(body.get("toWarehouse")).trim() : "");
    if (warehouse.isEmpty()) {
      return Result.error(400, "参数错误：目标仓库不能为空");
    }
    int quantity = 1;
    if (body.get("quantity") != null) {
      try {
        quantity = Math.max(1, Integer.parseInt(String.valueOf(body.get("quantity"))));
      } catch (NumberFormatException e) {
        return Result.error(400, "参数错误：数量必须为整数");
      }
    }
    // 预计入库日期（YYYY-MM-DD，可空）
    LocalDate expectedAt = null;
    if (body.get("expectedAt") != null && !String.valueOf(body.get("expectedAt")).isBlank()) {
      try {
        expectedAt = LocalDate.parse(String.valueOf(body.get("expectedAt")).trim());
      } catch (Exception e) {
        return Result.error(400, "参数错误：预计入库日期格式应为 YYYY-MM-DD");
      }
    }

    jdbcTemplate.update(
        "INSERT INTO mo_warehouse_inbound_plan (product_name, sku, warehouse_name, quantity, expected_at, remark) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
        productName,
        body.get("sku") == null ? null : String.valueOf(body.get("sku")),
        warehouse,
        quantity,
        expectedAt == null ? null : java.sql.Date.valueOf(expectedAt),
        body.get("remark") == null ? null : String.valueOf(body.get("remark")));

    return Result.success(Map.of("message", "已加入入仓计划"));
  }

  @Operation(summary = "入仓计划列表")
  @GetMapping("/warehouse/inbound-plan/list")
  public Result<Map<String, Object>> inboundPlanList(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String warehouse,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      StringBuilder where = new StringBuilder(" WHERE 1 = 1");
      List<Object> args = new ArrayList<>();
      if (status != null && !status.isBlank()) {
        where.append(" AND status = ?");
        args.add(status);
      }
      if (warehouse != null && !warehouse.isBlank()) {
        where.append(" AND warehouse_name = ?");
        args.add(warehouse);
      }

      // 总数
      Long total = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM mo_warehouse_inbound_plan" + where, Long.class, args.toArray());

      // 分页参数兜底，避免负偏移与超大分页
      int safePage = Math.max(1, page);
      int safeSize = size <= 0 ? 15 : Math.min(size, 100);
      int offset = (safePage - 1) * safeSize;

      List<Object> listArgs = new ArrayList<>(args);
      listArgs.add(safeSize);
      listArgs.add(offset);

      List<Map<String, Object>> records = jdbcTemplate.query(
          "SELECT id, product_name, sku, warehouse_name, quantity, expected_at, remark, status, " +
              "create_time, update_time FROM mo_warehouse_inbound_plan" + where +
              " ORDER BY create_time DESC LIMIT ? OFFSET ?",
          (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("productName", rs.getString("product_name"));
            item.put("sku", rs.getString("sku"));
            item.put("warehouseName", rs.getString("warehouse_name"));
            item.put("quantity", rs.getInt("quantity"));
            java.sql.Date expectedAt = rs.getDate("expected_at");
            item.put("expectedAt", expectedAt == null ? null : expectedAt.toLocalDate().toString());
            item.put("remark", rs.getString("remark"));
            item.put("status", rs.getString("status"));
            Timestamp createTime = rs.getTimestamp("create_time");
            item.put("createTime", createTime == null
                ? null : createTime.toLocalDateTime().toString().replace('T', ' '));
            Timestamp updateTime = rs.getTimestamp("update_time");
            item.put("updateTime", updateTime == null
                ? null : updateTime.toLocalDateTime().toString().replace('T', ' '));
            return item;
          },
          listArgs.toArray());

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("records", records);
      result.put("total", total == null ? 0L : total);
      result.put("page", safePage);
      result.put("size", safeSize);
      return Result.success(result);
    } catch (Exception e) {
      log.error("查询入仓计划失败", e);
      return Result.error("入仓计划查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "本地发货统计（今日待发货 / 已发货 / SLA / 承运商分布）")
  @GetMapping("/warehouse/shipping-stats")
  public Result<Map<String, Object>> warehouseShippingStats() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime todayEnd = todayStart.plusDays(1);

    // 今日待发货订单数
    long todayPending = orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, "PENDING_SHIP")
        .eq(OrderEntity::getDeleteStatus, 0));

    // 今日已发货订单（按发货时间统计）
    List<OrderEntity> todayShipped = orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
        .ge(OrderEntity::getDeliverTime, todayStart)
        .lt(OrderEntity::getDeliverTime, todayEnd)
        .eq(OrderEntity::getDeleteStatus, 0));

    // 近 30 天 SLA：支付后 48 小时内发货的占比
    List<OrderEntity> recentShipped = orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
        .isNotNull(OrderEntity::getPaidAt)
        .isNotNull(OrderEntity::getDeliverTime)
        .ge(OrderEntity::getDeliverTime, LocalDateTime.now().minusDays(30))
        .eq(OrderEntity::getDeleteStatus, 0));
    long onTime = recentShipped.stream()
        .filter(o -> ChronoUnit.HOURS.between(o.getPaidAt(), o.getDeliverTime()) <= 48)
        .count();
    double slaRate = recentShipped.isEmpty()
        ? 0d
        : Math.round(onTime * 1000.0 / recentShipped.size()) / 10.0;

    // 今日已发货按承运商分布
    Map<String, Long> byCarrier = new LinkedHashMap<>();
    for (OrderEntity o : todayShipped) {
      String carrier = o.getShippingCarrier();
      if (carrier == null || carrier.isBlank()) carrier = "未指定承运商";
      byCarrier.merge(carrier, 1L, Long::sum);
    }
    long totalShipped = byCarrier.values().stream().mapToLong(Long::longValue).sum();
    List<Map<String, Object>> carrierList = new ArrayList<>();
    for (Map.Entry<String, Long> entry : byCarrier.entrySet()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", entry.getKey());
      item.put("value", entry.getValue());
      item.put("ratio", totalShipped > 0
          ? Math.round(entry.getValue() * 1000.0 / totalShipped) / 10.0 : 0d);
      carrierList.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("todayPending", todayPending);
    result.put("shippedToday", todayShipped.size());
    result.put("slaRate", slaRate);
    // 字段名沿用前端模板：实际为按承运商的今日发货分布
    result.put("byWarehouse", carrierList);
    return Result.success(result);
  }

  @Operation(summary = "仓库品类库存分布")
  @GetMapping("/warehouse/category-stocks")
  public Result<Map<String, Object>> warehouseCategoryStocks(
      @RequestParam(required = false, defaultValue = "上海自营仓") String warehouse) {
    // 解析仓库名 → 仓库 ID
    WarehouseEntity targetWh = warehouseMapper.selectList(
        new LambdaQueryWrapper<WarehouseEntity>().eq(WarehouseEntity::getName, warehouse))
        .stream().findFirst().orElse(null);

    // 1) 找出该仓库下的所有库存记录（warehouse_id 维度）
    //    注：仓库名错配时不退化为全平台聚合，返回空 items + 错误 note，
    //    避免前端"美西仓库存分布"误展示全平台数据
    List<InventoryEntity> inventories = new ArrayList<>();
    if (targetWh != null) {
      inventories = inventoryMapper.selectList(
          new LambdaQueryWrapper<InventoryEntity>().eq(InventoryEntity::getWarehouseId, targetWh.getId()));
    }

    // 2) inventory.productId → 商品 categoryId（守卫：非空时再批量查）
    Set<Long> productIds = new HashSet<>();
    for (InventoryEntity inv : inventories) {
      if (inv.getProductId() != null) productIds.add(inv.getProductId());
    }
    Map<Long, Long> productToCategory = new HashMap<>();
    if (!productIds.isEmpty()) {
      List<ProductEntity> products = productMapper.selectByIds(productIds);
      for (ProductEntity p : products) {
        if (p.getCategoryId() != null) {
          productToCategory.put(p.getId(), p.getCategoryId());
        }
      }
    }

    // 3) 类目 id → 一级类目 id（递归上溯到 parentId == null）
    List<CategoryEntity> categories = categoryMapper.selectList(
        new LambdaQueryWrapper<CategoryEntity>().orderByAsc(CategoryEntity::getId));
    Map<Long, Long> catToRoot = new HashMap<>();
    Map<Long, CategoryEntity> catMap = new HashMap<>();
    for (CategoryEntity c : categories) {
      catMap.put(c.getId(), c);
    }
    for (CategoryEntity c : categories) {
      catToRoot.put(c.getId(), resolveRootCategoryId(c.getId(), catMap));
    }

    // 4) 按一级类目聚合 quantity
    Map<Long, Long> stockByRoot = new HashMap<>();
    for (InventoryEntity inv : inventories) {
      Long catId = productToCategory.get(inv.getProductId());
      if (catId == null) continue;
      Long rootId = catToRoot.getOrDefault(catId, 0L);
      stockByRoot.merge(rootId, (long) (inv.getQuantity() == null ? 0 : inv.getQuantity()), Long::sum);
    }

    // 5) 取 Top 类目构建分布（按库存降序）
    List<Map.Entry<Long, Long>> sorted = new ArrayList<>(stockByRoot.entrySet());
    sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    long total = sorted.stream().mapToLong(Map.Entry::getValue).sum();
    List<Map<String, Object>> items = new ArrayList<>();
    String[] palette = {"#FF7A45", "#40A9FF", "#52C41A", "#722ED1", "#FAAD14", "#13C2C2"};
    long shownTotal = 0;
    int topN = Math.min(5, sorted.size());
    for (int i = 0; i < topN; i++) {
      Map.Entry<Long, Long> e = sorted.get(i);
      CategoryEntity cat = catMap.get(e.getKey());
      String name = cat != null ? cat.getName() : ("类目-" + e.getKey());
      double percent = total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0d;
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", name);
      item.put("value", e.getValue());
      item.put("percent", percent);
      item.put("color", palette[i % palette.length]);
      items.add(item);
      shownTotal += e.getValue();
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", items);
    data.put("otherValue", Math.max(0, total - shownTotal));
    data.put("warehouse", warehouse);
    data.put("warehouseResolved", targetWh != null);
    if (targetWh == null) {
      // 仓库名错配：返回空 + 明确提示，前端可展示"未找到仓库"
      data.put("note", "未找到仓库「" + warehouse + "」，请检查仓库名称");
      data.put("error", "WAREHOUSE_NOT_FOUND");
    } else if (inventories.isEmpty()) {
      data.put("note", "仓库「" + warehouse + "」暂无库存数据");
    } else {
      data.put("note", "按仓库「" + warehouse + "」实际库存聚合");
    }
    return Result.success(data);
  }

  /**
   * 递归上溯到一级类目（parentId == null 即视为一级）。
   * 防止循环引用导致的栈溢出，最多迭代深度 10。
   */
  private Long resolveRootCategoryId(Long catId, Map<Long, CategoryEntity> catMap) {
    if (catId == null) return 0L;
    Long current = catId;
    for (int i = 0; i < 10; i++) {
      CategoryEntity c = catMap.get(current);
      if (c == null || c.getParentId() == null) {
        return current;
      }
      current = c.getParentId();
    }
    return current;
  }

  @Operation(summary = "智能选品建议（按销量 Top）")
  @GetMapping("/warehouse/smart-picks")
  public Result<List<Map<String, Object>>> warehouseSmartPicks(
      @RequestParam(defaultValue = "6") int limit) {
    int top = Math.max(1, Math.min(limit, 50));
    LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
        .eq(ProductEntity::getOnSale, true)
        .orderByDesc(ProductEntity::getSales)
        .last("LIMIT " + top);
    List<ProductEntity> products = productMapper.selectList(wrapper);

    // 启用中的仓库，用作真实库存推荐候选
    List<WarehouseEntity> activeWarehouses = warehouseMapper.selectList(
        new LambdaQueryWrapper<WarehouseEntity>()
            .eq(WarehouseEntity::getStatus, "ACTIVE")
            .orderByAsc(WarehouseEntity::getId));
    Map<Long, String> warehouseNames = new HashMap<>();
    for (WarehouseEntity w : activeWarehouses) {
      warehouseNames.put(w.getId(), w.getName());
    }

    List<Long> productIds = products.stream()
        .map(ProductEntity::getId)
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.toList());

    // 近 30 天真实销量（仅统计已完成订单）
    Map<Long, Long> sales30 = loadProductSalesLast30Days(productIds);

    // 按商品/仓库汇总真实可用库存（在库 - 锁定），并按商品汇总安全库存
    Map<Long, Integer> availableTotal = new HashMap<>();
    Map<Long, Integer> safetyTotal = new HashMap<>();
    Map<Long, Map<Long, Integer>> availableByWarehouse = new HashMap<>();
    if (!productIds.isEmpty()) {
      List<InventoryEntity> inventories = inventoryMapper.selectList(
          new LambdaQueryWrapper<InventoryEntity>().in(InventoryEntity::getProductId, productIds));
      for (InventoryEntity inv : inventories) {
        if (inv.getProductId() == null) continue;
        int quantity = inv.getQuantity() != null ? inv.getQuantity() : 0;
        int locked = inv.getLockedQuantity() != null ? inv.getLockedQuantity() : 0;
        int available = Math.max(0, quantity - locked);
        availableTotal.merge(inv.getProductId(), available, Integer::sum);
        safetyTotal.merge(inv.getProductId(), inv.getSafetyStock() != null ? inv.getSafetyStock() : 0, Integer::sum);
        if (inv.getWarehouseId() != null) {
          availableByWarehouse
              .computeIfAbsent(inv.getProductId(), k -> new HashMap<>())
              .merge(inv.getWarehouseId(), available, Integer::sum);
        }
      }
    }

    List<Map<String, Object>> list = new ArrayList<>();
    for (ProductEntity p : products) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", p.getId());
      item.put("name", p.getName());
      item.put("sku", p.getSpuCode());

      int available = availableTotal.getOrDefault(p.getId(), 0);
      int safety = safetyTotal.getOrDefault(p.getId(), 0);
      // 标签完全基于真实库存水位判定
      String tag;
      String tagClass;
      if (available <= 0) {
        tag = "无库存";
        tagClass = "tag-red";
      } else if ((safety > 0 && available < safety) || (safety == 0 && available < 10)) {
        tag = "需补货";
        tagClass = "tag-orange";
      } else {
        tag = "库存充足";
        tagClass = "tag-green";
      }
      item.put("tag", tag);
      item.put("tagClass", tagClass);

      // 推荐仓库：取该商品可用库存最多的启用仓库，无库存则不推荐
      Map<Long, Integer> warehouseStock = availableByWarehouse.getOrDefault(p.getId(), Collections.emptyMap());
      Long bestWarehouseId = warehouseStock.entrySet().stream()
          .filter(e -> warehouseNames.containsKey(e.getKey()) && e.getValue() > 0)
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse(null);
      item.put("warehouse", bestWarehouseId != null
          ? warehouseNames.get(bestWarehouseId)
          : "-");
      item.put("sales30", sales30.getOrDefault(p.getId(), 0L));
      item.put("availableStock", available);
      list.add(item);
    }
    return Result.success(list);
  }

  /** 近 30 天各商品真实销量（已完成订单），无数据返回 0 */
  private Map<Long, Long> loadProductSalesLast30Days(List<Long> productIds) {
    Map<Long, Long> result = new HashMap<>();
    if (productIds == null || productIds.isEmpty()) {
      return result;
    }
    String placeholders = String.join(",", Collections.nCopies(productIds.size(), "?"));
    List<Object> args = new ArrayList<>();
    args.add(Timestamp.valueOf(LocalDateTime.now().minusDays(30)));
    args.addAll(productIds);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
        "SELECT oi.product_id AS productId, COALESCE(SUM(oi.quantity), 0) AS qty "
            + "FROM mo_order_item oi JOIN mo_order o ON o.id = oi.order_id "
            + "WHERE o.status = 'COMPLETED' AND o.create_time >= ? "
            + "AND oi.product_id IN (" + placeholders + ") "
            + "GROUP BY oi.product_id",
        args.toArray());
    for (Map<String, Object> row : rows) {
      Object pid = row.get("productId") != null ? row.get("productId") : row.get("product_id");
      Object qty = row.get("qty");
      if (pid instanceof Number) {
        result.put(((Number) pid).longValue(), qty instanceof Number ? ((Number) qty).longValue() : 0L);
      }
    }
    return result;
  }

  @Operation(summary = "海外仓列表")
  @GetMapping("/overseas")
  public Result<List<Map<String, Object>>> overseasWarehouses(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<WarehouseEntity>()
          .eq(WarehouseEntity::getType, "OVERSEAS");
      // 形参 status 真正生效：仅在非空时叠加等值条件；走归一化兼容中文"启用/在售/停用/已下架"
      if (status != null && !status.isEmpty()) {
        wrapper.eq(WarehouseEntity::getStatus, normalizeEntityStatus(status));
      }
      List<WarehouseEntity> records = warehouseMapper.selectList(wrapper.orderByAsc(WarehouseEntity::getId));
      List<Map<String, Object>> list = new ArrayList<>();
      for (WarehouseEntity w : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", w.getId());
        item.put("name", w.getName());
        item.put("country", w.getCountry());
        item.put("skuCount", w.getSkuCount());
        item.put("totalStock", w.getTotalStock());
        item.put("usageRate", w.getUsageRate());
        item.put("status", w.getStatus());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("海外仓列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "拆包规则版本列表")
  @GetMapping("/split-package/versions")
  public Result<List<Map<String, Object>>> splitPackageVersions() {
    List<Map<String, Object>> list = jdbcTemplate.query(
        "SELECT id, version_code, description, enabled, creator, create_time " +
            "FROM mo_split_package_rule_version ORDER BY id DESC",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("versionCode", rs.getString("version_code"));
          item.put("description", rs.getString("description"));
          item.put("enabled", rs.getInt("enabled") == 1);
          item.put("creator", rs.getString("creator"));
          Timestamp ts = rs.getTimestamp("create_time");
          item.put("createTime", ts == null ? null : ts.toLocalDateTime().toString());
          return item;
        });
    return Result.success(list);
  }

  @Operation(summary = "拆包规则明细")
  @GetMapping("/split-package/rules")
  public Result<List<Map<String, Object>>> splitPackageRules(
      @RequestParam(required = false) Long versionId) {
    // 未指定版本时取最新启用版本
    Long targetVersionId = versionId;
    if (targetVersionId == null) {
      List<Long> ids = jdbcTemplate.query(
          "SELECT id FROM mo_split_package_rule_version WHERE enabled = 1 ORDER BY id DESC LIMIT 1",
          (rs, rowNum) -> rs.getLong("id"));
      if (!ids.isEmpty()) targetVersionId = ids.get(0);
    }
    if (targetVersionId == null) {
      return Result.success(Collections.emptyList());
    }
    List<Map<String, Object>> list = jdbcTemplate.query(
        "SELECT id, version_id, rule_name, trigger_type, trigger_value, action, priority, enabled " +
            "FROM mo_split_package_rule WHERE version_id = ? ORDER BY priority ASC",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("id", rs.getLong("id"));
          item.put("versionId", rs.getLong("version_id"));
          item.put("ruleName", rs.getString("rule_name"));
          item.put("triggerType", rs.getString("trigger_type"));
          item.put("triggerValue", rs.getString("trigger_value"));
          item.put("action", rs.getString("action"));
          item.put("priority", rs.getInt("priority"));
          item.put("enabled", rs.getInt("enabled"));
          return item;
        }, targetVersionId);
    return Result.success(list);
  }

  @Operation(summary = "新增拆包规则")
  @PostMapping("/split-package/rules")
  public Result<Map<String, Object>> createSplitPackageRule(@RequestBody Map<String, Object> body) {
    String ruleName = body.get("ruleName") == null ? "" : String.valueOf(body.get("ruleName")).trim();
    if (ruleName.isEmpty()) {
      return Result.error(400, "参数错误：规则名称不能为空");
    }
    String condition = body.get("condition") == null ? "" : String.valueOf(body.get("condition")).trim();
    // 约定格式："WEIGHT >10kg"：第一个空格前为触发类型，其余为触发值
    String triggerType = "CUSTOM";
    String triggerValue = condition;
    int splitIdx = condition.indexOf(' ');
    if (splitIdx > 0) {
      triggerType = condition.substring(0, splitIdx).toUpperCase();
      triggerValue = condition.substring(splitIdx + 1).trim();
    }

    Long versionId = resolveDefaultRuleVersionId();
    int priority = body.get("priority") == null ? 100 : Integer.parseInt(String.valueOf(body.get("priority")));
    boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));

    jdbcTemplate.update(
        "INSERT INTO mo_split_package_rule (version_id, rule_name, trigger_type, trigger_value, action, priority, enabled) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
        versionId, ruleName, triggerType, triggerValue,
        body.get("action") == null ? "SPLIT" : String.valueOf(body.get("action")),
        priority, enabled ? 1 : 0);

    return Result.success(Map.of("message", "规则已创建"));
  }

  @Operation(summary = "更新拆包规则")
  @PutMapping("/split-package/rules/{id}")
  public Result<Map<String, Object>> updateSplitPackageRule(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
    List<Long> existing = jdbcTemplate.query(
        "SELECT id FROM mo_split_package_rule WHERE id = ?",
        (rs, rowNum) -> rs.getLong("id"), id);
    if (existing.isEmpty()) {
      return Result.error(404, "规则不存在");
    }
    // 部分更新：仅更新传入字段
    if (body.get("ruleName") != null) {
      jdbcTemplate.update("UPDATE mo_split_package_rule SET rule_name = ? WHERE id = ?",
          String.valueOf(body.get("ruleName")), id);
    }
    if (body.get("condition") != null) {
      String condition = String.valueOf(body.get("condition")).trim();
      String triggerType = "CUSTOM";
      String triggerValue = condition;
      int splitIdx = condition.indexOf(' ');
      if (splitIdx > 0) {
        triggerType = condition.substring(0, splitIdx).toUpperCase();
        triggerValue = condition.substring(splitIdx + 1).trim();
      }
      jdbcTemplate.update("UPDATE mo_split_package_rule SET trigger_type = ?, trigger_value = ? WHERE id = ?",
          triggerType, triggerValue, id);
    }
    if (body.get("priority") != null) {
      jdbcTemplate.update("UPDATE mo_split_package_rule SET priority = ? WHERE id = ?",
          Integer.parseInt(String.valueOf(body.get("priority"))), id);
    }
    if (body.get("enabled") != null) {
      jdbcTemplate.update("UPDATE mo_split_package_rule SET enabled = ? WHERE id = ?",
          Boolean.parseBoolean(String.valueOf(body.get("enabled"))) ? 1 : 0, id);
    }
    return Result.success(Map.of("id", id, "message", "规则已更新"));
  }

  @Operation(summary = "删除拆包规则")
  @DeleteMapping("/split-package/rules/{id}")
  public Result<Map<String, Object>> deleteSplitPackageRule(@PathVariable Long id) {
    jdbcTemplate.update("DELETE FROM mo_split_package_rule WHERE id = ?", id);
    return Result.success(Map.of("id", id, "message", "规则已删除"));
  }

  /** 取当前启用的规则版本 ID（无则返回 1） */
  private Long resolveDefaultRuleVersionId() {
    List<Long> ids = jdbcTemplate.query(
        "SELECT id FROM mo_split_package_rule_version ORDER BY enabled DESC, id DESC LIMIT 1",
        (rs, rowNum) -> rs.getLong("id"));
    return ids.isEmpty() ? 1L : ids.get(0);
  }

  @Operation(summary = "拆包效果统计（本月）")
  @GetMapping("/split-package/stats")
  public Result<Map<String, Object>> splitPackageStats() {
    LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

    // 本月拆包裹单数 / 包裹总数 / 平均拆分数
    Map<String, Object> splitRow = jdbcTemplate.queryForMap(
        "SELECT COUNT(*) AS split_orders, COALESCE(SUM(split_count), 0) AS total_packages, " +
            "COALESCE(AVG(split_count), 0) AS avg_splits " +
            "FROM mo_split_package WHERE create_time >= ?", monthStart);
    long splitOrders = ((Number) splitRow.get("split_orders")).longValue();
    long totalPackages = ((Number) splitRow.get("total_packages")).longValue();
    double avgSplits = Math.round(((Number) splitRow.get("avg_splits")).doubleValue() * 10) / 10.0;

    // 本月订单数（拆包率分母）
    Long orderCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM mo_order WHERE delete_status = 0 AND create_time >= ?",
        Long.class, monthStart);
    long orders = orderCount == null ? 0L : orderCount;
    double splitRate = orders > 0 ? Math.round(splitOrders * 1000.0 / orders) / 10.0 : 0d;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("splitRate", splitRate);
    result.put("splitOrders", splitOrders);
    result.put("totalPackages", totalPackages);
    result.put("avgSplits", avgSplits);
    result.put("orderCount", orders);
    return Result.success(result);
  }

  @Operation(summary = "全局拆包策略配置")
  @GetMapping("/split-package/strategy")
  public Result<Map<String, Object>> splitPackageStrategy() {
    List<Map<String, Object>> rows = jdbcTemplate.query(
        "SELECT trigger_type, trigger_value, action, enabled FROM mo_split_package_rule " +
            "WHERE rule_name = '全局拆包策略' ORDER BY id DESC LIMIT 1",
        (rs, rowNum) -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("type", rs.getString("trigger_type"));
          item.put("param", rs.getString("trigger_value"));
          item.put("method", rs.getString("action"));
          item.put("enabled", rs.getInt("enabled") == 1);
          return item;
        });
    if (rows.isEmpty()) {
      Map<String, Object> defaults = new LinkedHashMap<>();
      defaults.put("type", "weight");
      defaults.put("param", "30");
      defaults.put("method", "weight-avg");
      defaults.put("enabled", true);
      defaults.put("exists", false);
      return Result.success(defaults);
    }
    Map<String, Object> result = rows.get(0);
    result.put("exists", true);
    return Result.success(result);
  }

  @Operation(summary = "保存全局拆包策略配置")
  @PutMapping("/split-package/strategy")
  public Result<Map<String, Object>> saveSplitPackageStrategy(@RequestBody Map<String, Object> body) {
    String type = body.get("type") == null ? "weight" : String.valueOf(body.get("type"));
    String param = body.get("param") == null ? "30" : String.valueOf(body.get("param"));
    String method = body.get("method") == null ? "weight-avg" : String.valueOf(body.get("method"));
    boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));

    // 取当前启用版本作为归属版本
    List<Long> versionIds = jdbcTemplate.query(
        "SELECT id FROM mo_split_package_rule_version ORDER BY enabled DESC, id DESC LIMIT 1",
        (rs, rowNum) -> rs.getLong("id"));
    long versionId = versionIds.isEmpty() ? 1L : versionIds.get(0);

    List<Long> existing = jdbcTemplate.query(
        "SELECT id FROM mo_split_package_rule WHERE rule_name = '全局拆包策略' ORDER BY id DESC LIMIT 1",
        (rs, rowNum) -> rs.getLong("id"));

    if (existing.isEmpty()) {
      jdbcTemplate.update(
          "INSERT INTO mo_split_package_rule (version_id, rule_name, trigger_type, trigger_value, action, priority, enabled) " +
              "VALUES (?, '全局拆包策略', ?, ?, ?, 0, ?)",
          versionId, type, param, method, enabled ? 1 : 0);
    } else {
      jdbcTemplate.update(
          "UPDATE mo_split_package_rule SET trigger_type = ?, trigger_value = ?, action = ?, enabled = ? WHERE id = ?",
          type, param, method, enabled ? 1 : 0, existing.get(0));
    }
    return Result.success(Map.of("message", "策略配置已保存"));
  }

  @Operation(summary = "合包管理列表")
  @GetMapping("/merge-packages")
  public Result<List<Map<String, Object>>> mergePackages(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      LambdaQueryWrapper<MergePackageEntity> wrapper =
          new LambdaQueryWrapper<MergePackageEntity>().orderByDesc(MergePackageEntity::getCreateTime);
      // 形参 status 真正生效：非空时叠加等值条件；走归一化兼容中文展示值
      if (status != null && !status.isEmpty()) {
        wrapper.eq(MergePackageEntity::getStatus, normalizeEntityStatus(status));
      }
      List<MergePackageEntity> records = mergePackageMapper.selectList(wrapper);
      List<Map<String, Object>> list = new ArrayList<>();
      for (MergePackageEntity m : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", m.getId());
        item.put("mergeNo", m.getMergeNo());
        item.put("orderCount", m.getOrderCount());
        item.put("packageCount", m.getPackageCount());
        item.put("totalWeight", m.getTotalWeight());
        item.put("status", m.getStatus());
        item.put("createTime", m.getCreateTime());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("合包列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "分包裹列表")
  @GetMapping("/split-packages")
  public Result<List<Map<String, Object>>> splitPackages(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      LambdaQueryWrapper<SplitPackageEntity> wrapper =
          new LambdaQueryWrapper<SplitPackageEntity>().orderByDesc(SplitPackageEntity::getCreateTime);
      // 形参 status 真正生效：非空时叠加等值条件；走归一化兼容中文展示值
      if (status != null && !status.isEmpty()) {
        wrapper.eq(SplitPackageEntity::getStatus, normalizeEntityStatus(status));
      }
      List<SplitPackageEntity> records = splitPackageMapper.selectList(wrapper);
      List<Map<String, Object>> list = new ArrayList<>();
      for (SplitPackageEntity s : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", s.getId());
        item.put("orderNo", s.getOrderNo());
        item.put("productCount", s.getProductCount());
        item.put("splitCount", s.getSplitCount());
        item.put("totalWeight", s.getTotalWeight());
        item.put("status", s.getStatus());
        item.put("createTime", s.getCreateTime());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("分包裹列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "承运商对比列表")
  @GetMapping("/carriers")
  public Result<List<Map<String, Object>>> carriers(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      List<CarrierEntity> records = adminCarrierService.listAll(status);
      List<Map<String, Object>> list = new ArrayList<>();
      for (CarrierEntity c : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("name", c.getName());
        item.put("code", c.getCode());
        item.put("transportMode", c.getTransportMode());
        item.put("avgDeliveryDays", c.getAvgDeliveryDays());
        item.put("firstWeightPrice", c.getFirstWeightPrice());
        item.put("renewWeightPrice", c.getRenewWeightPrice());
        item.put("praiseRate", c.getPraiseRate());
        // API 配置与产品编码（承运商级别的 channelId 用于覆盖 .env 全局默认）
        item.put("apiUserId", c.getApiUserId());
        // apiToken 仅在管理端展示用，不做脱敏处理（管理后台均为内部账号）
        item.put("apiToken", c.getApiToken());
        item.put("channelId", c.getChannelId());
        item.put("apiBaseUrl", c.getApiBaseUrl());
        item.put("labelApiEnabled", c.getLabelApiEnabled());
        item.put("apiRemark", c.getApiRemark());
        item.put("status", c.getStatus());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("承运商列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "清关管理列表")
  @GetMapping("/clearance")
  public Result<List<Map<String, Object>>> clearance(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      List<ClearanceEntity> records = adminClearanceService.listAll(status, false);
      List<Map<String, Object>> list = new ArrayList<>();
      for (ClearanceEntity c : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("declarationNo", c.getDeclarationNo());
        item.put("orderNo", c.getOrderNo());
        item.put("productName", c.getProductName());
        item.put("status", c.getStatus());
        item.put("declareTime", c.getDeclareTime());
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("清关列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "海关管理列表")
  @GetMapping("/customs")
  public Result<List<Map<String, Object>>> customs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      List<ClearanceEntity> records = adminClearanceService.listAll(status, true);
      List<Map<String, Object>> list = new ArrayList<>();
      for (ClearanceEntity c : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("hsCode", c.getHsCode());
        item.put("productName", c.getProductName());
        item.put("taxRate", c.getTaxRate());
        item.put("supervisionConditions", "");
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("海关列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "发货策略列表")
  @GetMapping("/shipping-strategies")
  public Result<List<Map<String, Object>>> shippingStrategies(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status) {
    try {
      List<ShippingStrategyEntity> records = adminShippingStrategyService.listAll(status);
      Map<Long, String> zoneNameMap = adminShippingStrategyService.listZoneNameMap();
      List<Map<String, Object>> list = new ArrayList<>();
      for (ShippingStrategyEntity s : records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", s.getId());
        // 兼容字段：前端使用 strategyName/shippingMethod/feeRule，旧调用方使用 name/method/rule
        item.put("name", s.getName());
        item.put("strategyName", s.getName());
        item.put("region", s.getRegion());
        item.put("zoneId", s.getZoneId());
        item.put("zoneName", zoneNameMap.getOrDefault(s.getZoneId(), "-"));
        item.put("method", s.getMethod());
        item.put("shippingMethod", s.getMethod());
        item.put("rule", s.getRuleDesc());
        item.put("feeRule", s.getRuleDesc());
        item.put("priority", s.getPriority());
        item.put("status", displayStrategyStatus(s.getStatus()));
        list.add(item);
      }
      // 手动分页
      int start = (page - 1) * size;
      int end = Math.min(start + size, list.size());
      List<Map<String, Object>> pageList = start < list.size() ? list.subList(start, end) : new ArrayList<>();
      return Result.success(pageList);
    } catch (Exception e) {
      return Result.error("发货策略列表查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "同步海关数据")
  @PostMapping("/{id}/customs/sync")
  public Result<Map<String, Object>> syncCustoms(@PathVariable Long id) {
    // 查询海关清关记录
    ClearanceEntity clearance = clearanceMapper.selectById(id);
    LocalDateTime syncTime = LocalDateTime.now();

    if (clearance != null) {
      // 更新状态为已同步，并记录同步时间
      clearance.setStatus("SYNCED");
      clearance.setClearanceTime(syncTime);
      clearanceMapper.updateById(clearance);
      log.info("海关数据同步成功, id={}, declarationNo={}, syncTime={}", id, clearance.getDeclarationNo(), syncTime);
    } else {
      log.warn("海关数据同步：未找到清关记录, id={}, 仅记录同步时间", id);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("syncTime", syncTime);
    result.put("message", clearance != null ? "海关数据同步成功" : "海关记录不存在，仅记录同步时间");
    return Result.success(result);
  }

  // ==================== 海外仓 CRUD ====================

  @Operation(summary = "创建海外仓")
  @PostMapping("/overseas")
  public Result<Map<String, Object>> createOverseas(@RequestBody Map<String, Object> body) {
    WarehouseEntity entity = new WarehouseEntity();
    entity.setType("OVERSEAS");
    entity.setName((String) body.get("name"));
    entity.setCountry((String) body.get("country"));
    entity.setCity((String) body.get("city"));
    entity.setAddress((String) body.get("address"));
    entity.setManager((String) body.get("manager"));
    entity.setPhone((String) body.get("phone"));
    if (body.get("skuCount") != null) entity.setSkuCount(Integer.valueOf(body.get("skuCount").toString()));
    if (body.get("totalStock") != null) entity.setTotalStock(Integer.valueOf(body.get("totalStock").toString()));
    if (body.get("usageRate") != null) entity.setUsageRate(Integer.valueOf(body.get("usageRate").toString()));
    entity.setStatus((String) body.get("status"));
    warehouseMapper.insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "海外仓创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新海外仓")
  @PutMapping("/overseas/{id}")
  public Result<Map<String, Object>> updateOverseas(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    WarehouseEntity entity = warehouseMapper.selectById(id);
    if (entity == null) {
      return Result.error("海外仓不存在");
    }
    if (body.get("name") != null) entity.setName((String) body.get("name"));
    if (body.get("country") != null) entity.setCountry((String) body.get("country"));
    if (body.get("city") != null) entity.setCity((String) body.get("city"));
    if (body.get("address") != null) entity.setAddress((String) body.get("address"));
    if (body.get("manager") != null) entity.setManager((String) body.get("manager"));
    if (body.get("phone") != null) entity.setPhone((String) body.get("phone"));
    if (body.get("skuCount") != null) entity.setSkuCount(Integer.valueOf(body.get("skuCount").toString()));
    if (body.get("totalStock") != null) entity.setTotalStock(Integer.valueOf(body.get("totalStock").toString()));
    if (body.get("usageRate") != null) entity.setUsageRate(Integer.valueOf(body.get("usageRate").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    warehouseMapper.updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海外仓更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除海外仓")
  @DeleteMapping("/overseas/{id}")
  public Result<Map<String, Object>> deleteOverseas(@PathVariable Long id) {
    warehouseMapper.deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海外仓删除成功");
    return Result.success(result);
  }

  // ==================== 合包 CRUD ====================

  @Operation(summary = "创建合包")
  @PostMapping("/merge-packages")
  public Result<Map<String, Object>> createMergePackage(@RequestBody Map<String, Object> body) {
    MergePackageEntity entity = new MergePackageEntity();
    entity.setMergeNo((String) body.get("mergeNo"));
    if (body.get("orderCount") != null) entity.setOrderCount(Integer.valueOf(body.get("orderCount").toString()));
    if (body.get("packageCount") != null) entity.setPackageCount(Integer.valueOf(body.get("packageCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    entity.setStatus((String) body.get("status"));
    mergePackageMapper.insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "合包创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新合包")
  @PutMapping("/merge-packages/{id}")
  public Result<Map<String, Object>> updateMergePackage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    MergePackageEntity entity = mergePackageMapper.selectById(id);
    if (entity == null) {
      return Result.error("合包记录不存在");
    }
    if (body.get("mergeNo") != null) entity.setMergeNo((String) body.get("mergeNo"));
    if (body.get("orderCount") != null) entity.setOrderCount(Integer.valueOf(body.get("orderCount").toString()));
    if (body.get("packageCount") != null) entity.setPackageCount(Integer.valueOf(body.get("packageCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    mergePackageMapper.updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "合包更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除合包")
  @DeleteMapping("/merge-packages/{id}")
  public Result<Map<String, Object>> deleteMergePackage(@PathVariable Long id) {
    mergePackageMapper.deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "合包删除成功");
    return Result.success(result);
  }

  // ==================== 分包裹 CRUD ====================

  @Operation(summary = "创建分包裹")
  @PostMapping("/split-packages")
  public Result<Map<String, Object>> createSplitPackage(@RequestBody Map<String, Object> body) {
    SplitPackageEntity entity = new SplitPackageEntity();
    entity.setOrderNo((String) body.get("orderNo"));
    if (body.get("productCount") != null) entity.setProductCount(Integer.valueOf(body.get("productCount").toString()));
    if (body.get("splitCount") != null) entity.setSplitCount(Integer.valueOf(body.get("splitCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    entity.setStatus((String) body.get("status"));
    splitPackageMapper.insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "分包裹创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新分包裹")
  @PutMapping("/split-packages/{id}")
  public Result<Map<String, Object>> updateSplitPackage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    SplitPackageEntity entity = splitPackageMapper.selectById(id);
    if (entity == null) {
      return Result.error("分包裹记录不存在");
    }
    if (body.get("orderNo") != null) entity.setOrderNo((String) body.get("orderNo"));
    if (body.get("productCount") != null) entity.setProductCount(Integer.valueOf(body.get("productCount").toString()));
    if (body.get("splitCount") != null) entity.setSplitCount(Integer.valueOf(body.get("splitCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    splitPackageMapper.updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "分包裹更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除分包裹")
  @DeleteMapping("/split-packages/{id}")
  public Result<Map<String, Object>> deleteSplitPackage(@PathVariable Long id) {
    splitPackageMapper.deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "分包裹删除成功");
    return Result.success(result);
  }

  // ==================== 承运商 CRUD ====================

  @Operation(summary = "创建承运商")
  @PostMapping("/carriers")
  public Result<Map<String, Object>> createCarrier(@Valid @RequestBody CarrierCreateRequest req) {
    CarrierEntity e = adminCarrierService.create(req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "承运商创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新承运商")
  @PutMapping("/carriers/{id}")
  public Result<Map<String, Object>> updateCarrier(@PathVariable Long id,
      @Valid @RequestBody CarrierUpdateRequest req) {
    CarrierEntity e = adminCarrierService.update(id, req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "承运商更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除承运商")
  @DeleteMapping("/carriers/{id}")
  public Result<Map<String, Object>> deleteCarrier(@PathVariable Long id) {
    adminCarrierService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "承运商删除成功");
    return Result.success(result);
  }

  // ==================== 清关 CRUD ====================

  @Operation(summary = "创建清关记录")
  @PostMapping("/clearance")
  public Result<Map<String, Object>> createClearance(@Valid @RequestBody ClearanceCreateRequest req) {
    ClearanceEntity e = adminClearanceService.create(req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "清关记录创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新清关记录")
  @PutMapping("/clearance/{id}")
  public Result<Map<String, Object>> updateClearance(@PathVariable Long id,
      @Valid @RequestBody ClearanceUpdateRequest req) {
    ClearanceEntity e = adminClearanceService.update(id, req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "清关记录更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除清关记录")
  @DeleteMapping("/clearance/{id}")
  public Result<Map<String, Object>> deleteClearance(@PathVariable Long id) {
    adminClearanceService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "清关记录删除成功");
    return Result.success(result);
  }

  // ==================== 海关 CRUD ====================
  // 注意：海关与清关共用 mo_clearance 表；customs 是"hsCode 非空"的查询视图，
  // 创建/更新/删除复用 AdminClearanceService，只是 url 路径前缀不同。
  @Operation(summary = "创建海关记录")
  @PostMapping("/customs")
  public Result<Map<String, Object>> createCustoms(@Valid @RequestBody ClearanceCreateRequest req) {
    ClearanceEntity e = adminClearanceService.create(req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "海关记录创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新海关记录")
  @PutMapping("/customs/{id}")
  public Result<Map<String, Object>> updateCustoms(@PathVariable Long id,
      @Valid @RequestBody ClearanceUpdateRequest req) {
    ClearanceEntity e = adminClearanceService.update(id, req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "海关记录更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除海关记录")
  @DeleteMapping("/customs/{id}")
  public Result<Map<String, Object>> deleteCustoms(@PathVariable Long id) {
    adminClearanceService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海关记录删除成功");
    return Result.success(result);
  }

  // ==================== 发货策略 CRUD ====================

  @Operation(summary = "创建发货策略")
  @PostMapping("/shipping-strategies")
  public Result<Map<String, Object>> createShippingStrategy(
      @Valid @RequestBody ShippingStrategyCreateRequest req) {
    ShippingStrategyEntity e = adminShippingStrategyService.create(req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "发货策略创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新发货策略")
  @PutMapping("/shipping-strategies/{id}")
  public Result<Map<String, Object>> updateShippingStrategy(@PathVariable Long id,
      @Valid @RequestBody ShippingStrategyUpdateRequest req) {
    ShippingStrategyEntity e = adminShippingStrategyService.update(id, req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "发货策略更新成功");
    return Result.success(result);
  }

  /**
   * 通用状态归一化：只映射仓库/承运商/合包/分包裹/清关等维度下无歧义的中文（启用/停用），
   * 避免"在售/已下架/缺货/正常/草稿/待审核"等商品/库存维度状态被误映射成 ACTIVE/INACTIVE。
   * 未识别值原样透传，由数据库决定是否命中。
   */
  private String normalizeEntityStatus(String status) {
    if (status == null || status.isEmpty()) return status;
    if ("启用".equals(status)) return "ACTIVE";
    if ("停用".equals(status)) return "INACTIVE";
    return status;
  }

  /** 存储状态 → 展示状态（前端表格期望中文） */
  private String displayStrategyStatus(String status) {
    if (status == null) return "启用";
    if ("ACTIVE".equals(status)) return "启用";
    if ("INACTIVE".equals(status)) return "停用";
    return status;
  }

  @Operation(summary = "删除发货策略")
  @DeleteMapping("/shipping-strategies/{id}")
  public Result<Map<String, Object>> deleteShippingStrategy(@PathVariable Long id) {
    adminShippingStrategyService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "发货策略删除成功");
    return Result.success(result);
  }

  // ==================== 发货区域（Shipping Zone）CRUD ====================

  @Operation(summary = "发货区域列表（策略页面下拉使用）")
  @GetMapping("/shipping-zones")
  public Result<List<Map<String, Object>>> shippingZones() {
    List<ShippingZoneEntity> records = adminShippingZoneService.listAll();
    List<Map<String, Object>> list = new ArrayList<>();
    for (ShippingZoneEntity z : records) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", z.getId());
      item.put("name", z.getName());
      item.put("countryCodes", z.getCountryCodes());
      item.put("status", z.getStatus());
      item.put("sortOrder", z.getSortOrder());
      item.put("remark", z.getRemark());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "创建发货区域")
  @PostMapping("/shipping-zones")
  public Result<Map<String, Object>> createShippingZone(@Valid @RequestBody ShippingZoneCreateRequest req) {
    ShippingZoneEntity e = adminShippingZoneService.create(req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "发货区域创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新发货区域")
  @PutMapping("/shipping-zones/{id}")
  public Result<Map<String, Object>> updateShippingZone(@PathVariable Long id,
      @Valid @RequestBody ShippingZoneUpdateRequest req) {
    ShippingZoneEntity e = adminShippingZoneService.update(id, req);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", e.getId());
    result.put("message", "发货区域更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除发货区域")
  @DeleteMapping("/shipping-zones/{id}")
  public Result<Map<String, Object>> deleteShippingZone(@PathVariable Long id) {
    // service 内部抛 BusinessException，被 GlobalExceptionHandler 统一映射为 409
    adminShippingZoneService.delete(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "发货区域删除成功");
    return Result.success(result);
  }
}
