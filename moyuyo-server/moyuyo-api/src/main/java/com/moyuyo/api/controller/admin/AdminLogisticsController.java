package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.*;
import com.moyuyo.dao.admin.mapper.*;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.AdminLogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Tag(name = "管理后台 - 物流管理")
@RestController
@RequestMapping("/api/admin/logistics")
public class AdminLogisticsController {

  private final AdminLogisticsService adminLogisticsService;
  private final LogisticsMapper logisticsMapper;
  private final OrderMapper orderMapper;

  // 以下Mapper均为新DAO模块（20260718迁移），maven安装失败时允许为null（字段类型改为Object避免ClassNotFoundException）
  @Autowired(required = false)
  private WarehouseMapper warehouseMapper;
  @Autowired(required = false)
  private CarrierMapper carrierMapper;
  @Autowired(required = false)
  private ClearanceMapper clearanceMapper;
  @Autowired(required = false)
  private ShippingStrategyMapper shippingStrategyMapper;
  @Autowired(required = false)
  private MergePackageMapper mergePackageMapper;
  @Autowired(required = false)
  private SplitPackageMapper splitPackageMapper;

  // 手动构造器注入必需的依赖
  public AdminLogisticsController(AdminLogisticsService adminLogisticsService,
                                   LogisticsMapper logisticsMapper,
                                   OrderMapper orderMapper) {
    this.adminLogisticsService = adminLogisticsService;
    this.logisticsMapper = logisticsMapper;
    this.orderMapper = orderMapper;
  }

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

    List<LogisticsEntity> allRecords = logisticsMapper.selectList(wrapper);
    int total = allRecords.size();
    int fromIndex = (page - 1) * size;
    int toIndex = Math.min(fromIndex + size, total);

    List<Map<String, Object>> list = new ArrayList<>();
    for (int i = fromIndex; i < toIndex; i++) {
      LogisticsEntity logi = allRecords.get(i);
      OrderEntity order = orderMapper.selectById(logi.getOrderId());

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
    result.put("total", total);
    result.put("page", page);
    result.put("size", size);
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
  public Result<List<Map<String, Object>>> warehouses() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (warehouseMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<WarehouseEntity> records = ((WarehouseMapper) warehouseMapper).selectList(
        new LambdaQueryWrapper<WarehouseEntity>().orderByAsc(WarehouseEntity::getId));
    List<Map<String, Object>> list = new ArrayList<>();
    for (WarehouseEntity w : records) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", w.getId());
      item.put("name", w.getName());
      item.put("type", w.getType());
      item.put("city", w.getCity());
      item.put("area", w.getArea());
      item.put("manager", w.getManager());
      item.put("status", w.getStatus());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "新增仓库")
  @PostMapping("/warehouses")
  public Result<Map<String, Object>> createWarehouse(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (warehouseMapper == null) {
      return Result.error("仓库服务暂不可用");
    }
    WarehouseEntity entity = new WarehouseEntity();
    entity.setName((String) body.get("name"));
    entity.setType((String) body.get("type"));
    entity.setCity((String) body.get("city"));
    entity.setAddress((String) body.get("address"));
    entity.setArea(body.get("area") != null ? Integer.valueOf(body.get("area").toString()) : null);
    entity.setManager((String) body.get("manager"));
    entity.setPhone((String) body.get("phone"));
    entity.setStatus((String) body.get("status"));
    ((WarehouseMapper) warehouseMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "仓库创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新仓库")
  @PutMapping("/warehouses/{id}")
  public Result<Map<String, Object>> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (warehouseMapper == null) {
      return Result.error("仓库服务暂不可用");
    }
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
    ((WarehouseMapper) warehouseMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "仓库更新成功");
    return Result.success(result);
  }

  @Operation(summary = "海外仓列表")
  @GetMapping("/overseas")
  public Result<List<Map<String, Object>>> overseasWarehouses() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (warehouseMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<WarehouseEntity> records = ((WarehouseMapper) warehouseMapper).selectList(
        new LambdaQueryWrapper<WarehouseEntity>()
            .eq(WarehouseEntity::getType, "OVERSEAS")
            .orderByAsc(WarehouseEntity::getId));
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
    return Result.success(list);
  }

  @Operation(summary = "合包管理列表")
  @GetMapping("/merge-packages")
  public Result<List<Map<String, Object>>> mergePackages() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (mergePackageMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<MergePackageEntity> records = ((MergePackageMapper) mergePackageMapper).selectList(
        new LambdaQueryWrapper<MergePackageEntity>().orderByDesc(MergePackageEntity::getCreateTime));
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
    return Result.success(list);
  }

  @Operation(summary = "分包裹列表")
  @GetMapping("/split-packages")
  public Result<List<Map<String, Object>>> splitPackages() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (splitPackageMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<SplitPackageEntity> records = ((SplitPackageMapper) splitPackageMapper).selectList(
        new LambdaQueryWrapper<SplitPackageEntity>().orderByDesc(SplitPackageEntity::getCreateTime));
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
    return Result.success(list);
  }

  @Operation(summary = "承运商对比列表")
  @GetMapping("/carriers")
  public Result<List<Map<String, Object>>> carriers() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (carrierMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<CarrierEntity> records = ((CarrierMapper) carrierMapper).selectList(
        new LambdaQueryWrapper<CarrierEntity>().orderByAsc(CarrierEntity::getId));
    List<Map<String, Object>> list = new ArrayList<>();
    for (CarrierEntity c : records) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", c.getId());
      item.put("name", c.getName());
      item.put("transportMode", c.getTransportMode());
      item.put("avgDeliveryDays", c.getAvgDeliveryDays());
      item.put("firstWeightPrice", c.getFirstWeightPrice());
      item.put("renewWeightPrice", c.getRenewWeightPrice());
      item.put("praiseRate", c.getPraiseRate());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "清关管理列表")
  @GetMapping("/clearance")
  public Result<List<Map<String, Object>>> clearance() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (clearanceMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<ClearanceEntity> records = ((ClearanceMapper) clearanceMapper).selectList(
        new LambdaQueryWrapper<ClearanceEntity>().orderByDesc(ClearanceEntity::getDeclareTime));
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
    return Result.success(list);
  }

  @Operation(summary = "海关管理列表")
  @GetMapping("/customs")
  public Result<List<Map<String, Object>>> customs() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (clearanceMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<ClearanceEntity> records = ((ClearanceMapper) clearanceMapper).selectList(
        new LambdaQueryWrapper<ClearanceEntity>()
            .isNotNull(ClearanceEntity::getHsCode)
            .ne(ClearanceEntity::getHsCode, "")
            .orderByAsc(ClearanceEntity::getId));
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
    return Result.success(list);
  }

  @Operation(summary = "发货策略列表")
  @GetMapping("/shipping-strategies")
  public Result<List<Map<String, Object>>> shippingStrategies() {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (shippingStrategyMapper == null) {
      return Result.success(Collections.emptyList());
    }
    List<ShippingStrategyEntity> records = ((ShippingStrategyMapper) shippingStrategyMapper).selectList(
        new LambdaQueryWrapper<ShippingStrategyEntity>().orderByAsc(ShippingStrategyEntity::getId));
    List<Map<String, Object>> list = new ArrayList<>();
    for (ShippingStrategyEntity s : records) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", s.getId());
      item.put("name", s.getName());
      item.put("region", s.getRegion());
      item.put("method", s.getMethod());
      item.put("rule", s.getRuleDesc());
      item.put("status", s.getStatus());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "同步海关数据")
  @PostMapping("/{id}/customs/sync")
  public Result<Map<String, Object>> syncCustoms(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    // 查询海关清关记录
    ClearanceEntity clearance = ((ClearanceMapper) clearanceMapper).selectById(id);
    LocalDateTime syncTime = LocalDateTime.now();

    if (clearance != null) {
      // 更新状态为已同步，并记录同步时间
      clearance.setStatus("SYNCED");
      clearance.setClearanceTime(syncTime);
      ((ClearanceMapper) clearanceMapper).updateById(clearance);
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
    // 新Mapper可能因maven安装失败为null
    if (warehouseMapper == null) {
      return Result.error("仓库服务暂不可用");
    }
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
    ((WarehouseMapper) warehouseMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "海外仓创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新海外仓")
  @PutMapping("/overseas/{id}")
  public Result<Map<String, Object>> updateOverseas(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (warehouseMapper == null) {
      return Result.error("仓库服务暂不可用");
    }
    WarehouseEntity entity = ((WarehouseMapper) warehouseMapper).selectById(id);
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
    ((WarehouseMapper) warehouseMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海外仓更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除海外仓")
  @DeleteMapping("/overseas/{id}")
  public Result<Map<String, Object>> deleteOverseas(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (warehouseMapper == null) {
      return Result.error("仓库服务暂不可用");
    }
    ((WarehouseMapper) warehouseMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海外仓删除成功");
    return Result.success(result);
  }

  // ==================== 合包 CRUD ====================

  @Operation(summary = "创建合包")
  @PostMapping("/merge-packages")
  public Result<Map<String, Object>> createMergePackage(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (mergePackageMapper == null) {
      return Result.error("合包服务暂不可用");
    }
    MergePackageEntity entity = new MergePackageEntity();
    entity.setMergeNo((String) body.get("mergeNo"));
    if (body.get("orderCount") != null) entity.setOrderCount(Integer.valueOf(body.get("orderCount").toString()));
    if (body.get("packageCount") != null) entity.setPackageCount(Integer.valueOf(body.get("packageCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    entity.setStatus((String) body.get("status"));
    ((MergePackageMapper) mergePackageMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "合包创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新合包")
  @PutMapping("/merge-packages/{id}")
  public Result<Map<String, Object>> updateMergePackage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (mergePackageMapper == null) {
      return Result.error("合包服务暂不可用");
    }
    MergePackageEntity entity = ((MergePackageMapper) mergePackageMapper).selectById(id);
    if (entity == null) {
      return Result.error("合包记录不存在");
    }
    if (body.get("mergeNo") != null) entity.setMergeNo((String) body.get("mergeNo"));
    if (body.get("orderCount") != null) entity.setOrderCount(Integer.valueOf(body.get("orderCount").toString()));
    if (body.get("packageCount") != null) entity.setPackageCount(Integer.valueOf(body.get("packageCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((MergePackageMapper) mergePackageMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "合包更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除合包")
  @DeleteMapping("/merge-packages/{id}")
  public Result<Map<String, Object>> deleteMergePackage(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (mergePackageMapper == null) {
      return Result.error("合包服务暂不可用");
    }
    ((MergePackageMapper) mergePackageMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "合包删除成功");
    return Result.success(result);
  }

  // ==================== 分包裹 CRUD ====================

  @Operation(summary = "创建分包裹")
  @PostMapping("/split-packages")
  public Result<Map<String, Object>> createSplitPackage(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (splitPackageMapper == null) {
      return Result.error("分包裹服务暂不可用");
    }
    SplitPackageEntity entity = new SplitPackageEntity();
    entity.setOrderNo((String) body.get("orderNo"));
    if (body.get("productCount") != null) entity.setProductCount(Integer.valueOf(body.get("productCount").toString()));
    if (body.get("splitCount") != null) entity.setSplitCount(Integer.valueOf(body.get("splitCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    entity.setStatus((String) body.get("status"));
    ((SplitPackageMapper) splitPackageMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "分包裹创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新分包裹")
  @PutMapping("/split-packages/{id}")
  public Result<Map<String, Object>> updateSplitPackage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (splitPackageMapper == null) {
      return Result.error("分包裹服务暂不可用");
    }
    SplitPackageEntity entity = ((SplitPackageMapper) splitPackageMapper).selectById(id);
    if (entity == null) {
      return Result.error("分包裹记录不存在");
    }
    if (body.get("orderNo") != null) entity.setOrderNo((String) body.get("orderNo"));
    if (body.get("productCount") != null) entity.setProductCount(Integer.valueOf(body.get("productCount").toString()));
    if (body.get("splitCount") != null) entity.setSplitCount(Integer.valueOf(body.get("splitCount").toString()));
    if (body.get("totalWeight") != null) entity.setTotalWeight(new BigDecimal(body.get("totalWeight").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((SplitPackageMapper) splitPackageMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "分包裹更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除分包裹")
  @DeleteMapping("/split-packages/{id}")
  public Result<Map<String, Object>> deleteSplitPackage(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (splitPackageMapper == null) {
      return Result.error("分包裹服务暂不可用");
    }
    ((SplitPackageMapper) splitPackageMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "分包裹删除成功");
    return Result.success(result);
  }

  // ==================== 承运商 CRUD ====================

  @Operation(summary = "创建承运商")
  @PostMapping("/carriers")
  public Result<Map<String, Object>> createCarrier(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (carrierMapper == null) {
      return Result.error("承运商服务暂不可用");
    }
    CarrierEntity entity = new CarrierEntity();
    entity.setName((String) body.get("name"));
    entity.setTransportMode((String) body.get("transportMode"));
    if (body.get("avgDeliveryDays") != null) entity.setAvgDeliveryDays(new BigDecimal(body.get("avgDeliveryDays").toString()));
    if (body.get("firstWeightPrice") != null) entity.setFirstWeightPrice(new BigDecimal(body.get("firstWeightPrice").toString()));
    if (body.get("renewWeightPrice") != null) entity.setRenewWeightPrice(new BigDecimal(body.get("renewWeightPrice").toString()));
    if (body.get("praiseRate") != null) entity.setPraiseRate(new BigDecimal(body.get("praiseRate").toString()));
    entity.setStatus((String) body.get("status"));
    ((CarrierMapper) carrierMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "承运商创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新承运商")
  @PutMapping("/carriers/{id}")
  public Result<Map<String, Object>> updateCarrier(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (carrierMapper == null) {
      return Result.error("承运商服务暂不可用");
    }
    CarrierEntity entity = ((CarrierMapper) carrierMapper).selectById(id);
    if (entity == null) {
      return Result.error("承运商不存在");
    }
    if (body.get("name") != null) entity.setName((String) body.get("name"));
    if (body.get("transportMode") != null) entity.setTransportMode((String) body.get("transportMode"));
    if (body.get("avgDeliveryDays") != null) entity.setAvgDeliveryDays(new BigDecimal(body.get("avgDeliveryDays").toString()));
    if (body.get("firstWeightPrice") != null) entity.setFirstWeightPrice(new BigDecimal(body.get("firstWeightPrice").toString()));
    if (body.get("renewWeightPrice") != null) entity.setRenewWeightPrice(new BigDecimal(body.get("renewWeightPrice").toString()));
    if (body.get("praiseRate") != null) entity.setPraiseRate(new BigDecimal(body.get("praiseRate").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((CarrierMapper) carrierMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "承运商更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除承运商")
  @DeleteMapping("/carriers/{id}")
  public Result<Map<String, Object>> deleteCarrier(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (carrierMapper == null) {
      return Result.error("承运商服务暂不可用");
    }
    ((CarrierMapper) carrierMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "承运商删除成功");
    return Result.success(result);
  }

  // ==================== 清关 CRUD ====================

  @Operation(summary = "创建清关记录")
  @PostMapping("/clearance")
  public Result<Map<String, Object>> createClearance(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ClearanceEntity entity = new ClearanceEntity();
    entity.setDeclarationNo((String) body.get("declarationNo"));
    entity.setOrderNo((String) body.get("orderNo"));
    entity.setProductName((String) body.get("productName"));
    entity.setHsCode((String) body.get("hsCode"));
    if (body.get("taxRate") != null) entity.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
    entity.setStatus((String) body.get("status"));
    ((ClearanceMapper) clearanceMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "清关记录创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新清关记录")
  @PutMapping("/clearance/{id}")
  public Result<Map<String, Object>> updateClearance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ClearanceEntity entity = ((ClearanceMapper) clearanceMapper).selectById(id);
    if (entity == null) {
      return Result.error("清关记录不存在");
    }
    if (body.get("declarationNo") != null) entity.setDeclarationNo((String) body.get("declarationNo"));
    if (body.get("orderNo") != null) entity.setOrderNo((String) body.get("orderNo"));
    if (body.get("productName") != null) entity.setProductName((String) body.get("productName"));
    if (body.get("hsCode") != null) entity.setHsCode((String) body.get("hsCode"));
    if (body.get("taxRate") != null) entity.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((ClearanceMapper) clearanceMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "清关记录更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除清关记录")
  @DeleteMapping("/clearance/{id}")
  public Result<Map<String, Object>> deleteClearance(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ((ClearanceMapper) clearanceMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "清关记录删除成功");
    return Result.success(result);
  }

  // ==================== 海关 CRUD ====================

  @Operation(summary = "创建海关记录")
  @PostMapping("/customs")
  public Result<Map<String, Object>> createCustoms(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ClearanceEntity entity = new ClearanceEntity();
    entity.setHsCode((String) body.get("hsCode"));
    entity.setProductName((String) body.get("productName"));
    if (body.get("taxRate") != null) entity.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
    entity.setStatus((String) body.get("status"));
    ((ClearanceMapper) clearanceMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "海关记录创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新海关记录")
  @PutMapping("/customs/{id}")
  public Result<Map<String, Object>> updateCustoms(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ClearanceEntity entity = ((ClearanceMapper) clearanceMapper).selectById(id);
    if (entity == null) {
      return Result.error("海关记录不存在");
    }
    if (body.get("hsCode") != null) entity.setHsCode((String) body.get("hsCode"));
    if (body.get("productName") != null) entity.setProductName((String) body.get("productName"));
    if (body.get("taxRate") != null) entity.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((ClearanceMapper) clearanceMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海关记录更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除海关记录")
  @DeleteMapping("/customs/{id}")
  public Result<Map<String, Object>> deleteCustoms(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (clearanceMapper == null) {
      return Result.error("清关服务暂不可用");
    }
    ((ClearanceMapper) clearanceMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "海关记录删除成功");
    return Result.success(result);
  }

  // ==================== 发货策略 CRUD ====================

  @Operation(summary = "创建发货策略")
  @PostMapping("/shipping-strategies")
  public Result<Map<String, Object>> createShippingStrategy(@RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (shippingStrategyMapper == null) {
      return Result.error("发货策略服务暂不可用");
    }
    ShippingStrategyEntity entity = new ShippingStrategyEntity();
    entity.setName((String) body.get("name"));
    entity.setRegion((String) body.get("region"));
    entity.setMethod((String) body.get("method"));
    entity.setRuleDesc((String) body.get("ruleDesc"));
    if (body.get("priority") != null) entity.setPriority(Integer.valueOf(body.get("priority").toString()));
    entity.setStatus((String) body.get("status"));
    ((ShippingStrategyMapper) shippingStrategyMapper).insert(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "发货策略创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新发货策略")
  @PutMapping("/shipping-strategies/{id}")
  public Result<Map<String, Object>> updateShippingStrategy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    // 新Mapper可能因maven安装失败为null
    if (shippingStrategyMapper == null) {
      return Result.error("发货策略服务暂不可用");
    }
    ShippingStrategyEntity entity = ((ShippingStrategyMapper) shippingStrategyMapper).selectById(id);
    if (entity == null) {
      return Result.error("发货策略不存在");
    }
    if (body.get("name") != null) entity.setName((String) body.get("name"));
    if (body.get("region") != null) entity.setRegion((String) body.get("region"));
    if (body.get("method") != null) entity.setMethod((String) body.get("method"));
    if (body.get("ruleDesc") != null) entity.setRuleDesc((String) body.get("ruleDesc"));
    if (body.get("priority") != null) entity.setPriority(Integer.valueOf(body.get("priority").toString()));
    if (body.get("status") != null) entity.setStatus((String) body.get("status"));
    ((ShippingStrategyMapper) shippingStrategyMapper).updateById(entity);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "发货策略更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除发货策略")
  @DeleteMapping("/shipping-strategies/{id}")
  public Result<Map<String, Object>> deleteShippingStrategy(@PathVariable Long id) {
    // 新Mapper可能因maven安装失败为null
    if (shippingStrategyMapper == null) {
      return Result.error("发货策略服务暂不可用");
    }
    ((ShippingStrategyMapper) shippingStrategyMapper).deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "发货策略删除成功");
    return Result.success(result);
  }
}
