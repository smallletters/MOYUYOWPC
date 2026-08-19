package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.RefundEntity;
import com.moyuyo.dao.mapper.OrderMapper;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

  private final OrderMapper orderMapper;

  @Operation(summary = "退款统计数据")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    try {
      // P1 性能修复：原实现 selectList(全表) + in-memory grouping，10 万行退款会导致 50MB Java 堆分配 + OOM
      // 改为 MySQL 端 GROUP BY + SUM 一次查询，让数据库做聚合，避免拉全表到 JVM
      List<Map<String, Object>> aggRows = refundMapper.selectMaps(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
              .select("status", "COUNT(*) AS cnt", "COALESCE(SUM(amount), 0) AS amt")
              .groupBy("status"));

      long totalRefunds = 0L;
      long pendingCount = 0L;
      long approvedCount = 0L;
      long rejectedCount = 0L;
      long completedCount = 0L;
      BigDecimal totalAmount = BigDecimal.ZERO;
      for (Map<String, Object> row : aggRows) {
        Object statusObj = row.get("status");
        Object cntObj = row.get("cnt");
        Object amtObj = row.get("amt");
        if (statusObj == null || cntObj == null) continue;
        long cnt = ((Number) cntObj).longValue();
        totalRefunds += cnt;
        String status = statusObj.toString();
        switch (status) {
          case "PENDING" -> pendingCount = cnt;
          case "APPROVED" -> approvedCount = cnt;
          case "REJECTED" -> rejectedCount = cnt;
          case "COMPLETED" -> {
            completedCount = cnt;
            if (amtObj != null) {
              totalAmount = new BigDecimal(amtObj.toString());
            }
          }
          default -> { /* 其他状态仅计入 totalRefunds，不细分 */ }
        }
      }

      // 阻塞项 #1：补齐今日退款金额 / 退款率 / 平均处理时长三个 KPI 字段
      // 1) todayAmount：今日 COMPLETED 的累计退款金额
      LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
      Map<String, Object> todayAgg = refundMapper.selectMaps(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
              .select("COALESCE(SUM(amount), 0) AS todayAmt")
              .eq("status", "COMPLETED")
              .ge("complete_time", todayStart)).stream().findFirst().orElse(new HashMap<>());
      BigDecimal todayAmount = todayAgg.get("todayAmt") == null
          ? BigDecimal.ZERO
          : new BigDecimal(todayAgg.get("todayAmt").toString()).setScale(2, RoundingMode.HALF_UP);

      // 2) refundRate：近 30 天退款单数 / 近 30 天支付订单数
      long recentRefunds = refundMapper.selectCount(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
              .ge("create_time", todayStart.minusDays(30)));
      long recentPaidOrders = orderMapper.selectCount(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.moyuyo.dao.entity.OrderEntity>()
              .ge("create_time", todayStart.minusDays(30))
              .in("status", "PAID", "RECEIVED", "REFUNDING", "REFUNDED", "COMPLETED"));
      String refundRate = recentPaidOrders > 0
          ? String.format("%.2f%%", recentRefunds * 10000.0 / recentPaidOrders / 100.0)
          : "0%";

      // 3) avgProcessTime：COMPLETED 退款 create_time→complete_time 平均处理时长（小时）
      List<Map<String, Object>> procRows = refundMapper.selectMaps(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
              .select("create_time", "complete_time")
              .eq("status", "COMPLETED")
              .isNotNull("complete_time")
              .last("LIMIT 1000"));
      double avgHours = 0d;
      if (!procRows.isEmpty()) {
        long totalSec = 0L;
        int counted = 0;
        for (Map<String, Object> row : procRows) {
          Object ctObj = row.get("create_time");
          Object ftObj = row.get("complete_time");
          if (ctObj == null || ftObj == null) continue;
          LocalDateTime ct = toLocalDateTime(ctObj);
          LocalDateTime ft = toLocalDateTime(ftObj);
          if (ct == null || ft == null) continue;
          totalSec += Duration.between(ct, ft).getSeconds();
          counted++;
        }
        if (counted > 0) {
          avgHours = totalSec / 3600.0 / counted;
        }
      }
      String avgProcessTime = String.format("%.1fh", avgHours);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("totalRefunds", totalRefunds);
      result.put("pendingCount", pendingCount);
      result.put("approvedCount", approvedCount);
      result.put("rejectedCount", rejectedCount);
      result.put("completedCount", completedCount);
      result.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP).toString());
      result.put("todayAmount", todayAmount.toString());
      result.put("refundRate", refundRate);
      result.put("avgProcessTime", avgProcessTime);
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
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type) {
    try {
      // 阻塞项 #7：分页参数守卫，防止 size=100000 触发全表扫描 + OOM
      int[] pageParams = PageParamGuard.normalize(page, size, 10);
      // type 与 status 解耦透传给 Service，由 Service 做白名单校验
      return Result.success(refundService.listAllRefunds(pageParams[0], pageParams[1], status, type));
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
      // #10：详情透出拒绝/完成审计字段
      item.put("rejectReason", entity.getRejectReason());
      item.put("rejectOperatorId", entity.getRejectOperatorId());
      item.put("rejectTime", entity.getRejectTime());
      item.put("completeOperatorId", entity.getCompleteOperatorId());
      item.put("transactionId", entity.getTransactionId());
      return Result.success(item);
    } catch (Exception e) {
      return Result.error("查询退款详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "按 type 维度的状态精确计数（用于 chip 角标）")
  @GetMapping("/status-count")
  public Result<Map<String, Long>> statusCount(@RequestParam(required = false) String type) {
    try {
      // type 由 Service 层做白名单校验与归一化
      return Result.success(refundService.countRefundsByStatus(type));
    } catch (Exception e) {
      return Result.error("查询退款状态计数失败: " + e.getMessage());
    }
  }

  @Operation(summary = "退款原因分布")
  @GetMapping("/reason-distribution")
  public Result<List<Map<String, Object>>> reasonDistribution() {
    try {
      // 性能修复：原实现 selectList(全表) + in-memory grouping，10 万行退款会导致 OOM
      // 改为 MySQL 端 GROUP BY 聚合，让数据库做 group by + count(*)
      List<Map<String, Object>> rows = refundMapper.selectMaps(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RefundEntity>()
              .select("COALESCE(NULLIF(reason, ''), '其他') AS reason", "COUNT(*) AS cnt")
              .groupBy("COALESCE(NULLIF(reason, ''), '其他')")
              .orderByDesc("cnt"));

      List<Map<String, Object>> list = rows.stream()
          .map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reason", row.get("reason"));
            item.put("count", row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).intValue());
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

  @Operation(summary = "完成退款（财务录入第三方流水号）")
  @PutMapping("/{id}/complete")
  public Result<Map<String, Object>> complete(
      @PathVariable Long id, @RequestParam String transactionId) {
    try {
      if (transactionId == null || transactionId.trim().isEmpty()) {
        return Result.error("请填写第三方退款流水号");
      }
      refundService.completeRefund(id, UserContextHolder.getUserId(), transactionId.trim());
      return Result.success(Map.of("id", id, "message", "退款已完成"));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      log.error("完成退款失败 refundId={}", id, e);
      return Result.error("完成退款失败: " + e.getMessage());
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

  /**
   * 数据库时间字段兼容处理：MySQL JDBC 驱动在 8.x 默认返回 {@link LocalDateTime}；
   * 兼容历史返回 {@link java.sql.Timestamp} 的场景，统一转换为 LocalDateTime。
   */
  private LocalDateTime toLocalDateTime(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDateTime) return (LocalDateTime) value;
    if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
    if (value instanceof java.util.Date) return ((java.util.Date) value).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
    return null;
  }
}
