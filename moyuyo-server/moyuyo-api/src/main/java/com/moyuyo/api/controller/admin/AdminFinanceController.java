package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.finance.SettlementDetailResponse;
import com.moyuyo.common.dto.admin.finance.SettlementRequest;
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
  public Result<Map<String, Object>> overview() {
    try {
      Map<String, Object> svcResult = financeService.getFinanceOverview();
      // 将Service返回的key映射为前端期望的字段
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("totalRevenue", svcResult.getOrDefault("monthGmv", BigDecimal.ZERO));
      result.put("actualIncome", svcResult.getOrDefault("actualIncome", BigDecimal.ZERO));
      result.put("pendingSettlement", svcResult.getOrDefault("pendingSettlement", BigDecimal.ZERO));
      // 从数据库查询已完成的结算数量
      Long completedCount = settlementMapper.selectCount(
        new LambdaQueryWrapper<SettlementEntity>()
          .eq(SettlementEntity::getStatus, "COMPLETED"));
      result.put("completedSettlements", completedCount != null ? completedCount.intValue() : 0);
      result.put("pendingCount", svcResult.getOrDefault("pendingIssues", 0));
      result.put("refundAmount", svcResult.getOrDefault("refundAmount", BigDecimal.ZERO));
      result.put("channelDistribution", svcResult.getOrDefault("channelDistribution", Collections.emptyList()));
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询财务概览失败: " + e.getMessage());
    }
  }

  @Operation(summary = "结算明细列表")
  @GetMapping("/settlements")
  public Result<Map<String, Object>> settlements(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      // 分页查询结算记录
      Page<SettlementEntity> pageResult = settlementMapper.selectPage(
        new Page<>(page, size),
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
  public Result<Map<String, Object>> settlementDetail(@PathVariable Long id) {
    try {
      // 从数据库查询结算记录
      SettlementEntity settlement = settlementMapper.selectById(id);
      if (settlement == null) {
        return Result.error("结算记录不存在");
      }

      // 构建基础数据
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", settlement.getId());
      data.put("settlementNo", settlement.getSettlementNo());
      data.put("period", settlement.getPeriod());

      // amount 字段作为总金额，计算手续费和净额
      BigDecimal totalAmount = settlement.getAmount() != null
        ? BigDecimal.valueOf(settlement.getAmount()) : BigDecimal.ZERO;
      BigDecimal fee = totalAmount.multiply(BigDecimal.valueOf(0.01))
        .setScale(2, RoundingMode.HALF_UP);
      BigDecimal netAmount = totalAmount.subtract(fee);

      data.put("totalAmount", totalAmount);
      data.put("fee", fee);
      data.put("netAmount", netAmount);
      data.put("status", settlement.getStatus());
      data.put("settleTime", settlement.getSettleTime() != null
        ? settlement.getSettleTime() : settlement.getCreateTime());

      // 查询结算周期内的相关订单
      List<Map<String, Object>> orders = new ArrayList<>();
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
            Map<String, Object> orderItem = new LinkedHashMap<>();
            orderItem.put("orderNo", order.getOrderNo());
            orderItem.put("amount", order.getPayAmount() != null
              ? order.getPayAmount() : BigDecimal.ZERO);
            // 估算手续费（1%）
            BigDecimal orderFee = order.getPayAmount() != null
              ? order.getPayAmount().multiply(BigDecimal.valueOf(0.01))
                .setScale(2, RoundingMode.HALF_UP)
              : BigDecimal.ZERO;
            orderItem.put("fee", orderFee);
            orderItem.put("payTime", order.getPaidAt() != null
              ? order.getPaidAt() : order.getCreateTime());
            orders.add(orderItem);
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
                Map<String, Object> ordItem = new LinkedHashMap<>();
                ordItem.put("orderNo", ord.getOrderNo());
                ordItem.put("amount", ord.getPayAmount() != null ? ord.getPayAmount() : BigDecimal.ZERO);
                BigDecimal ordFee = ord.getPayAmount() != null
                  ? ord.getPayAmount().multiply(BigDecimal.valueOf(0.01)).setScale(2, RoundingMode.HALF_UP)
                  : BigDecimal.ZERO;
                ordItem.put("fee", ordFee);
                ordItem.put("payTime", ord.getPaidAt() != null ? ord.getPaidAt() : ord.getCreateTime());
                orders.add(ordItem);
              }
            }
          } catch (Exception ex) {
            // 所有格式都无法解析，使用空列表
          }
        }
      }

      data.put("orderCount", orders.size());
      data.put("orders", orders);
      return Result.success(data);
    } catch (Exception e) {
      return Result.error("查询结算详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "创建结算记录")
  @PostMapping("/settlements")
  public Result<Map<String, Object>> createSettlement(@RequestBody Map<String, Object> body) {
    try {
      SettlementEntity entity = new SettlementEntity();
      // 生成结算单号: SET-年月日格式
      String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      entity.setSettlementNo("SET-" + datePart);
      entity.setPeriod((String) body.get("period"));
      if (body.get("amount") != null) {
        entity.setAmount(Double.valueOf(body.get("amount").toString()));
      }
      entity.setStatus((String) body.get("status"));
      entity.setRemark((String) body.get("remark"));
      if (body.get("payChannel") != null) {
        entity.setPayChannel((String) body.get("payChannel"));
      }

      settlementMapper.insert(entity);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entity.getId());
      result.put("settlementNo", entity.getSettlementNo());
      result.put("message", "结算记录创建成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("创建结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新结算记录")
  @PutMapping("/settlements/{id}")
  public Result<Map<String, Object>> updateSettlement(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      SettlementEntity entity = settlementMapper.selectById(id);
      if (entity == null) {
        return Result.error("结算记录不存在");
      }

      if (body.get("period") != null) entity.setPeriod((String) body.get("period"));
      if (body.get("amount") != null) entity.setAmount(Double.valueOf(body.get("amount").toString()));
      if (body.get("status") != null) entity.setStatus((String) body.get("status"));
      if (body.get("remark") != null) entity.setRemark((String) body.get("remark"));
      if (body.get("payChannel") != null) entity.setPayChannel((String) body.get("payChannel"));

      settlementMapper.updateById(entity);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "结算记录更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除结算记录")
  @DeleteMapping("/settlements/{id}")
  public Result<Map<String, Object>> deleteSettlement(@PathVariable Long id) {
    try {
      settlementMapper.deleteById(id);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "结算记录删除成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("删除结算记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "交易记录列表")
  @GetMapping("/records")
  public Result<List<Map<String, Object>>> records(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    List<Map<String, Object>> list = new ArrayList<>();
    // 从 mo_finance_record 表查询真实交易记录，按 createTime 降序排列
    List<FinanceRecordEntity> recordList = financeRecordMapper.selectList(
      new LambdaQueryWrapper<FinanceRecordEntity>()
        .orderByDesc(FinanceRecordEntity::getCreateTime));
    for (FinanceRecordEntity record : recordList) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", record.getId());
      item.put("tradeNo", record.getOrderNo());
      item.put("type", record.getType());
      item.put("amount", record.getAmount());
      item.put("createTime", record.getCreateTime());
      list.add(item);
    }
    return Result.success(list);
  }
}
