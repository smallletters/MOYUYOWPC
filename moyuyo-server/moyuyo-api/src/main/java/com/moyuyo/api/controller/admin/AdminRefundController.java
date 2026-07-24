package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 退款管理")
@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

  private final RefundService refundService;

  private final RefundMapper refundMapper;

  @Operation(summary = "退款统计数据")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    try {
      // 查询总退款数
      Long totalRefunds = refundMapper.selectCount(new LambdaQueryWrapper<>());

      // 按状态分组查询各状态数量
      Map<String, Long> statusCounts = refundMapper.selectList(new LambdaQueryWrapper<>())
        .stream()
        .collect(Collectors.groupingBy(RefundEntity::getStatus, Collectors.counting()));

      // 计算退款总金额
      List<RefundEntity> allList = refundMapper.selectList(
        new LambdaQueryWrapper<RefundEntity>()
          .eq(RefundEntity::getStatus, "COMPLETED"));
      BigDecimal totalAmount = allList.stream()
        .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("totalRefunds", totalRefunds);
      result.put("pendingCount", statusCounts.getOrDefault("PENDING", 0L));
      result.put("approvedCount", statusCounts.getOrDefault("APPROVED", 0L));
      result.put("rejectedCount", statusCounts.getOrDefault("REJECTED", 0L));
      result.put("completedCount", statusCounts.getOrDefault("COMPLETED", 0L));
      result.put("totalAmount", totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询退款统计失败: " + e.getMessage());
    }
  }

  @Operation(summary = "退款列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {
    try {
      return Result.success(refundService.listAllRefunds(page, size, status));
    } catch (Exception e) {
      return Result.error("查询退款列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "退款原因分布")
  @GetMapping("/reason-distribution")
  public Result<List<Map<String, Object>>> reasonDistribution() {
    try {
      // 从数据库查询所有退款记录，按 reason 字段分组统计
      List<RefundEntity> allRefunds = refundMapper.selectList(new LambdaQueryWrapper<>());

      // 按退款原因分组计数
      Map<String, Long> reasonCounts = allRefunds.stream()
        .filter(r -> r.getReason() != null && !r.getReason().isEmpty())
        .collect(Collectors.groupingBy(RefundEntity::getReason, Collectors.counting()));

      // 将结果排序（数量降序）
      List<Map<String, Object>> list = reasonCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .map(entry -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("reason", entry.getKey());
          item.put("count", entry.getValue().intValue());
          return item;
        })
        .collect(Collectors.toList());

      return Result.success(list);
    } catch (Exception e) {
      return Result.error("查询退款原因分布失败: " + e.getMessage());
    }
  }

  @Operation(summary = "同意退款")
  @PutMapping("/{id}/approve")
  public Result<Map<String, Object>> approve(@PathVariable Long id) {
    try {
      refundService.approveRefund(id, 0L);
      return Result.success(Map.of("id", id, "message", "退款已批准"));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("同意退款失败: " + e.getMessage());
    }
  }

  @Operation(summary = "拒绝退款")
  @PutMapping("/{id}/reject")
  public Result<Map<String, Object>> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
    try {
      String reason = body != null ? body.get("reason") : null;
      refundService.rejectRefund(id, 0L, reason);
      return Result.success(Map.of("id", id, "message", "退款已拒绝"));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("拒绝退款失败: " + e.getMessage());
    }
  }

  @Operation(summary = "批量同意退款")
  @PutMapping("/batch-approve")
  public Result<Map<String, Object>> batchApprove(@RequestBody Map<String, Object> body) {
    try {
      List<Long> ids = new ArrayList<>();
      Object idsObj = body.get("ids");
      if (idsObj instanceof List) {
        for (Object item : (List<?>) idsObj) {
          if (item instanceof Number) {
            ids.add(((Number) item).longValue());
          }
        }
      }
      if (ids.isEmpty()) {
        return Result.error("请选择要批准的退款");
      }
      int success = 0;
      int fail = 0;
      for (Long id : ids) {
        try {
          refundService.approveRefund(id, 0L);
          success++;
        } catch (Exception e) {
          fail++;
        }
      }
      return Result.success(Map.of("success", success, "fail", fail, "message", "批量处理完成"));
    } catch (Exception e) {
      return Result.error("批量同意退款失败: " + e.getMessage());
    }
  }
}
