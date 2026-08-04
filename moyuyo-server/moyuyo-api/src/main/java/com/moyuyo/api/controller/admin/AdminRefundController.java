package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.RefundMapper;
import com.moyuyo.service.RefundService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 退款管理")
@Slf4j
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

      // 按状态分组查询各状态数量（一次性查询避免重复全表扫描）
      List<RefundEntity> allRefunds = refundMapper.selectList(new LambdaQueryWrapper<>());
      Map<String, Long> statusCounts = allRefunds.stream()
        .collect(Collectors.groupingBy(RefundEntity::getStatus, Collectors.counting()));

      // 计算退款总金额（仅已完成退款）
      BigDecimal totalAmount = allRefunds.stream()
        .filter(r -> "COMPLETED".equals(r.getStatus()))
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

  @Operation(summary = "退款详情")
  @GetMapping("/{id}")
  public Result<Map<String, Object>> detail(@PathVariable Long id) {
    try {
      RefundEntity entity = refundMapper.selectById(id);
      if (entity == null) {
        return Result.error("退款记录不存在");
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", entity.getId());
      item.put("orderId", entity.getOrderId());
      item.put("refundNo", entity.getRefundNo());
      item.put("type", entity.getType());
      item.put("amount", entity.getAmount() != null ? entity.getAmount().toString() : "0.00");
      item.put("reason", entity.getReason());
      item.put("description", entity.getDescription());
      item.put("images", entity.getImages());
      item.put("status", entity.getStatus());
      item.put("wooRefundId", entity.getWooRefundId());
      item.put("createTime", entity.getCreateTime());
      item.put("completeTime", entity.getCompleteTime());
      return Result.success(item);
    } catch (Exception e) {
      return Result.error("查询退款详情失败: " + e.getMessage());
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
      refundService.approveRefund(id, UserContextHolder.getUserId());
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
      refundService.rejectRefund(id, UserContextHolder.getUserId(), reason);
      return Result.success(Map.of("id", id, "message", "退款已拒绝"));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("拒绝退款失败: " + e.getMessage());
    }
  }

  @Operation(summary = "批量同意退款")
  @PutMapping("/batch-approve")
  @RateLimiter(name = "refundBatch", fallbackMethod = "refundRateLimitFallback")
  public Result<Map<String, Object>> batchApprove(@RequestBody Map<String, Object> body) {
    try {
      List<Long> ids = new ArrayList<>();
      Object idsObj = body.get("ids");
      if (idsObj instanceof List) {
        for (Object item : (List<?>) idsObj) {
          if (item instanceof Number) {
            ids.add(((Number) item).longValue());
          } else if (item instanceof String) {
            try {
              ids.add(Long.valueOf((String) item));
            } catch (NumberFormatException ignored) {
              // 忽略无法解析的字符串项
            }
          }
        }
      }
      if (ids.isEmpty()) {
        return Result.error("请选择要批准的退款");
      }
      int success = 0;
      int fail = 0;
      Long operatorId = UserContextHolder.getUserId();
      for (Long id : ids) {
        try {
          refundService.approveRefund(id, operatorId);
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

  /**
   * 退款批量操作限流降级：dev 20/分钟，prod 5/分钟（详见 application.yml）
   */
  @SuppressWarnings("unused")
  private Result<Map<String, Object>> refundRateLimitFallback(Map<String, Object> body, RequestNotPermitted e) {
    log.warn("退款批量接口触发限流：{}", e.getMessage());
    return Result.error(429, "退款批量操作过于频繁，请稍后再试");
  }
}
