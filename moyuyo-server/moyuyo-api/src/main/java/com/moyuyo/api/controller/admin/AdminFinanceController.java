package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.finance.FinanceOverviewResponse;
import com.moyuyo.common.dto.admin.finance.SettlementDetailResponse;
import com.moyuyo.common.dto.admin.finance.SettlementRequest;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.admin.entity.FinanceRecordEntity;
import com.moyuyo.dao.admin.entity.SettlementEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
import com.moyuyo.dao.admin.mapper.SettlementMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Tag(name = "管理后台 - 财务管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {

  private final FinanceService financeService;
  private final OrderMapper orderMapper;
  private final SettlementMapper settlementMapper;
  private final FinanceRecordMapper financeRecordMapper;

  @Operation(summary = "财务概览")
  @GetMapping("/overview")
  public Result<FinanceOverviewResponse> overview() {
    try {
      Map<String, Object> svcResult = financeService.getFinanceOverview();
      // 将 Service 返回的原始 Map 映射为强类型 VO
      FinanceOverviewResponse result = new FinanceOverviewResponse();
      result.setTotalRevenue(toBigDecimal(svcResult.get("monthGmv")));
      result.setActualIncome(toBigDecimal(svcResult.get("actualIncome")));
      result.setPendingSettlement(toBigDecimal(svcResult.get("pendingSettlement")));
      // 从数据库查询已完成的结算数量
      // 修正：mo_settlement 状态机为 PENDING/SETTLING/SETTLED/ABNORMAL，没有 COMPLETED
      // seed 数据与代码应统一使用 SETTLED（与 SettlementEntity 注释一致）
      Long completedCount = settlementMapper.selectCount(
        new LambdaQueryWrapper<SettlementEntity>()
          .eq(SettlementEntity::getStatus, "SETTLED"));
      result.setCompletedSettlements(completedCount != null ? completedCount.intValue() : 0);
      Object pendingIssues = svcResult.get("pendingIssues");
      result.setPendingCount(pendingIssues instanceof Number ? ((Number) pendingIssues).intValue() : 0);
      result.setRefundAmount(toBigDecimal(svcResult.get("refundAmount")));
      Object cd = svcResult.get("channelDistribution");
      if (cd instanceof List) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cdList = (List<Map<String, Object>>) cd;
        result.setChannelDistribution(cdList);
      } else {
        result.setChannelDistribution(Collections.emptyList());
      }
      // 附加辅助分析数据（退款原因分布、最近 6 月趋势），便于概览页直接展示
      result.setRefundReasonDistribution(financeService.getRefundReasonDistribution());
      result.setMonthlyTrend(financeService.getMonthlyTrend());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询财务概览失败: " + e.getMessage());
    }
  }

  /** 将 Object 安全转换为 BigDecimal,空值返回 ZERO */
  private static BigDecimal toBigDecimal(Object obj) {
    if (obj == null) return BigDecimal.ZERO;
    if (obj instanceof BigDecimal) return (BigDecimal) obj;
    if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
    try {
      return new BigDecimal(obj.toString());
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  @Operation(summary = "结算明细列表")
  @GetMapping("/settlements")
  public Result<Map<String, Object>> settlements(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      // 分页参数统一守卫：避免 size=100000 触发 OOM / 全表扫描
      int[] pageParams = PageParamGuard.normalize(page, size, 15);
      // 分页查询结算记录
      Page<SettlementEntity> pageResult = settlementMapper.selectPage(
        new Page<>(pageParams[0], pageParams[1]),
        new LambdaQueryWrapper<SettlementEntity>()
          .orderByDesc(SettlementEntity::getCreateTime));
      
      List<Map<String, Object>> list = new ArrayList<>();
      for (SettlementEntity settlement : pageResult.getRecords()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", settlement.getId());
        item.put("settlementNo", settlement.getSettlementNo());
        item.put("period", settlement.getPeriod());
        item.put("amount", settlement.getAmount());
        item.put("status", settlement.getStatus());
        item.put("settleTime", settlement.getSettleTime());
        list.add(item);
      }
      
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("records", list);
      result.put("total", pageResult.getTotal());
      result.put("page", pageResult.getCurrent());
      result.put("size", pageResult.getSize());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询结算列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "结算详情")
  @GetMapping("/settlements/{id}")
  public Result<SettlementDetailResponse> settlementDetail(@PathVariable Long id) {
    try {
      // 从数据库查询结算记录
      SettlementEntity settlement = settlementMapper.selectById(id);
      if (settlement == null) {
        return Result.error("结算记录不存在");
      }

      // 构建详情 VO
      SettlementDetailResponse data = new SettlementDetailResponse();
      data.setId(settlement.getId());
      data.setSettlementNo(settlement.getSettlementNo());
      data.setPeriod(settlement.getPeriod());

      // amount 字段作为总金额，计算手续费和净额(统一2位小数)
      BigDecimal totalAmount = settlement.getAmount() != null
        ? BigDecimal.valueOf(settlement.getAmount()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
      BigDecimal fee = totalAmount.multiply(BigDecimal.valueOf(0.01))
        .setScale(2, RoundingMode.HALF_UP);
      BigDecimal netAmount = totalAmount.subtract(fee);

      data.setTotalAmount(totalAmount);
      data.setFee(fee);
      data.setNetAmount(netAmount);
      data.setStatus(settlement.getStatus());
      data.setSettleTime(settlement.getSettleTime() != null
        ? settlement.getSettleTime() : settlement.getCreateTime());

      // 查询结算周期内的相关订单
      List<SettlementDetailResponse.OrderSummary> orders = new ArrayList<>();
      if (settlement.getPeriod() != null) {
        try {
          // 尝试解析周期日期（如 "2026-06-15"），查询该天的订单
          LocalDate periodDate = LocalDate.parse(settlement.getPeriod());
          LocalDateTime dayStart = LocalDateTime.of(periodDate, LocalTime.MIN);
          LocalDateTime dayEnd = LocalDateTime.of(periodDate, LocalTime.MAX);

          List<OrderEntity> orderList = orderMapper.selectList(
            new LambdaQueryWrapper<OrderEntity>()
              .ge(OrderEntity::getCreateTime, dayStart)
              .le(OrderEntity::getCreateTime, dayEnd)
              .orderByDesc(OrderEntity::getCreateTime));

          for (OrderEntity order : orderList) {
            orders.add(toOrderSummary(order));
          }
        } catch (DateTimeParseException e) {
          // 尝试解析周期范围格式（如 "2026-06-01~2026-06-15" 或 "2026-06-01 to 2026-06-15"）
          try {
            String periodStr = settlement.getPeriod();
            String[] parts = null;
            if (periodStr.contains("~")) {
              parts = periodStr.split("~");
            } else if (periodStr.toLowerCase().contains(" to ")) {
              parts = periodStr.split("(?i) to ");
            }
            if (parts != null && parts.length == 2) {
              LocalDate startDate = LocalDate.parse(parts[0].trim());
              LocalDate endDate = LocalDate.parse(parts[1].trim());
              LocalDateTime rangeStart = LocalDateTime.of(startDate, LocalTime.MIN);
              LocalDateTime rangeEnd = LocalDateTime.of(endDate, LocalTime.MAX);
              List<OrderEntity> rangedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<OrderEntity>()
                  .ge(OrderEntity::getCreateTime, rangeStart)
                  .le(OrderEntity::getCreateTime, rangeEnd)
                  .orderByDesc(OrderEntity::getCreateTime));
              for (OrderEntity ord : rangedOrders) {
                orders.add(toOrderSummary(ord));
              }
            }
          } catch (Exception ex) {
            // 所有格式都无法解析，使用空列表
          }
        }
      }

      data.setOrderCount(orders.size());
      data.setOrders(orders);
      return Result.success(data);
    } catch (Exception e) {
      return Result.error("查询结算详情失败: " + e.getMessage());
    }
  }

  /** 将订单实体转换为结算详情中的订单摘要 */
  private static SettlementDetailResponse.OrderSummary toOrderSummary(OrderEntity order) {
    SettlementDetailResponse.OrderSummary summary = new SettlementDetailResponse.OrderSummary();
    summary.setOrderNo(order.getOrderNo());
    summary.setAmount(order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO);
    // 估算手续费(1%)
    BigDecimal orderFee = order.getPayAmount() != null
      ? order.getPayAmount().multiply(BigDecimal.valueOf(0.01)).setScale(2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    summary.setFee(orderFee);
    summary.setPayTime(order.getPaidAt() != null ? order.getPaidAt() : order.getCreateTime());
    return summary;
  }

  @Operation(summary = "创建结算记录")
  @PostMapping("/settlements")
  public Result<OperationResult> createSettlement(@RequestBody SettlementRequest request) {
    try {
      SettlementEntity entity = new SettlementEntity();
      // 生成结算单号: SET-年月日格式
      String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      entity.setSettlementNo("SET-" + datePart);
      entity.setPeriod(request.getPeriod());
      entity.setAmount(request.getAmount());
      entity.setStatus(request.getStatus());
      entity.setRemark(request.getRemark());
      entity.setPayChannel(request.getPayChannel());

      settlementMapper.insert(entity);

      OperationResult result = new OperationResult();
      result.setId(entity.getId());
      result.setMessage("结算记录创建成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("创建结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新结算记录")
  @PutMapping("/settlements/{id}")
  public Result<OperationResult> updateSettlement(@PathVariable Long id, @RequestBody SettlementRequest request) {
    try {
      SettlementEntity entity = settlementMapper.selectById(id);
      if (entity == null) {
        return Result.error("结算记录不存在");
      }

      // 仅更新非空字段(与原 Map 逻辑保持一致)
      if (request.getPeriod() != null) entity.setPeriod(request.getPeriod());
      if (request.getAmount() != null) entity.setAmount(request.getAmount());
      if (request.getStatus() != null) entity.setStatus(request.getStatus());
      if (request.getRemark() != null) entity.setRemark(request.getRemark());
      if (request.getPayChannel() != null) entity.setPayChannel(request.getPayChannel());

      settlementMapper.updateById(entity);

      OperationResult result = new OperationResult();
      result.setId(id);
      result.setMessage("结算记录更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除结算记录")
  @DeleteMapping("/settlements/{id}")
  public Result<OperationResult> deleteSettlement(@PathVariable Long id) {
    try {
      settlementMapper.deleteById(id);
      OperationResult result = new OperationResult();
      result.setId(id);
      result.setMessage("结算记录删除成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("删除结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "交易记录列表")
  @GetMapping("/records")
  public Result<Map<String, Object>> records(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // 使用 MyBatis-Plus Page 进行数据库分页查询
    Page<FinanceRecordEntity> pageResult = financeRecordMapper.selectPage(
      new Page<>(page, size),
      new LambdaQueryWrapper<FinanceRecordEntity>()
        .orderByDesc(FinanceRecordEntity::getCreateTime));

    List<Map<String, Object>> list = new ArrayList<>();
    for (FinanceRecordEntity record : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", record.getId());
      item.put("tradeNo", record.getOrderNo());
      item.put("type", record.getType());
      item.put("amount", record.getAmount());
      item.put("createTime", record.getCreateTime());
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("records", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }
}
