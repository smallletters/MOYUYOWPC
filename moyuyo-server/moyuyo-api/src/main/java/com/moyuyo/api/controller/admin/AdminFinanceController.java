package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.FinanceRecordEntity;
import com.moyuyo.dao.admin.entity.SettlementEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
import com.moyuyo.dao.admin.mapper.SettlementMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/admin/finance")
public class AdminFinanceController {

    private final FinanceService financeService;

    private final OrderMapper orderMapper;

    // 新DAO模块maven安装失败时允许为null，避免ClassNotFoundException
    @Autowired(required = false)
  private SettlementMapper settlementMapper;

  @Autowired(required = false)
  private FinanceRecordMapper financeRecordMapper;

    // 手动构造器注入必需的依赖
    public AdminFinanceController(FinanceService financeService,
                                   OrderMapper orderMapper) {
        this.financeService = financeService;
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "财务概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        try {
            return Result.success(financeService.getFinanceOverview());
        } catch (Exception e) {
            return Result.error("查询财务概览失败: " + e.getMessage());
        }
    }

    @Operation(summary = "结算明细列表")
    @GetMapping("/settlements")
    public Result<List<Map<String, Object>>> settlements(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        try {
            return Result.success(financeService.getSettlementList());
        } catch (Exception e) {
            return Result.error("查询结算列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "结算详情")
    @GetMapping("/settlements/{id}")
    public Result<Map<String, Object>> settlementDetail(@PathVariable Long id) {
        try {
            // 新Mapper可能因maven安装失败为null，返回空数据
            if (settlementMapper == null) {
                return Result.error("结算服务暂不可用");
            }
            // 从数据库查询结算记录
            SettlementEntity settlement = ((SettlementMapper) settlementMapper).selectById(id);
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
                    // 周期格式不标准，使用空列表
                    // TODO: 可根据实际结算周期格式调整解析逻辑
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
            // 新Mapper可能因maven安装失败为null
            if (settlementMapper == null) {
                return Result.error("结算服务暂不可用");
            }
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

            ((SettlementMapper) settlementMapper).insert(entity);

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
            // 新Mapper可能因maven安装失败为null
            if (settlementMapper == null) {
                return Result.error("结算服务暂不可用");
            }
            SettlementEntity entity = ((SettlementMapper) settlementMapper).selectById(id);
            if (entity == null) {
                return Result.error("结算记录不存在");
            }

            if (body.get("period") != null) entity.setPeriod((String) body.get("period"));
            if (body.get("amount") != null) entity.setAmount(Double.valueOf(body.get("amount").toString()));
            if (body.get("status") != null) entity.setStatus((String) body.get("status"));
            if (body.get("remark") != null) entity.setRemark((String) body.get("remark"));
            if (body.get("payChannel") != null) entity.setPayChannel((String) body.get("payChannel"));

            ((SettlementMapper) settlementMapper).updateById(entity);

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
            // 新Mapper可能因maven安装失败为null
            if (settlementMapper == null) {
                return Result.error("结算服务暂不可用");
            }
            ((SettlementMapper) settlementMapper).deleteById(id);
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
        // 新Mapper可能因maven安装失败为null，返回空列表
        if (financeRecordMapper == null) {
            return Result.success(list);
        }
        // 从 mo_finance_record 表查询真实交易记录，按 createTime 降序排列
        List<FinanceRecordEntity> recordList = ((FinanceRecordMapper) financeRecordMapper).selectList(
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
