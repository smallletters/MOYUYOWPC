package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 商品举报
 * 数据源：mo_product_report（V20261110_02 迁移创建）
 */
@Slf4j
@Tag(name = "管理后台 - 商品举报")
@RestController
@RequestMapping("/api/admin/product-report")
@RequiredArgsConstructor
public class AdminProductReportController {

  private final JdbcTemplate jdbcTemplate;

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /** 举报类型 → 中文 + 样式类 */
  private static final Map<String, String[]> TYPE_META = Map.of(
      "FAKE_AD", new String[]{"虚假宣传", "fake-ad"},
      "QUALITY", new String[]{"质量问题", "quality"},
      "INFRINGEMENT", new String[]{"侵权投诉", "infringement"},
      "BANNED", new String[]{"违禁品", "banned"}
  );

  /** 状态 → 中文 + 样式类 */
  private static final Map<String, String[]> STATUS_META = Map.of(
      "PENDING", new String[]{"待处理", "pending"},
      "PROCESSING", new String[]{"处理中", "processing"},
      "DONE", new String[]{"已完成", "done"}
  );

  @Operation(summary = "商品举报列表")
  @GetMapping("/list")
  public Result<List<Map<String, Object>>> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status) {
    StringBuilder sql = new StringBuilder(
        "SELECT id, product_id, product_name, report_type, reason, reporter, status, " +
            "handle_result, handle_comment, sla_deadline, create_time " +
            "FROM mo_product_report WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) {
      sql.append(" AND report_type = ?");
      args.add(type);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND status = ?");
      args.add(status);
    }
    sql.append(" ORDER BY create_time DESC");

    try {
      List<Map<String, Object>> list = jdbcTemplate.query(sql.toString(),
          (rs, rowNum) -> toVO(rs), args.toArray());
      return Result.success(list);
    } catch (Exception e) {
      log.error("查询商品举报失败", e);
      return Result.error("商品举报查询失败: " + e.getMessage());
    }
  }

  @Operation(summary = "处理商品举报")
  @PutMapping("/{id}/handle")
  public Result<Map<String, Object>> handle(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
    String result = body.get("result") == null ? "" : String.valueOf(body.get("result")).trim();
    if (result.isEmpty()) {
      return Result.error(400, "参数错误：处理结果不能为空");
    }
    String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
    try {
      int rows = jdbcTemplate.update(
          "UPDATE mo_product_report SET status = 'DONE', handle_result = ?, handle_comment = ?, " +
              "sla_deadline = NULL WHERE id = ?",
          result, comment, id);
      if (rows == 0) {
        return Result.error(404, "举报记录不存在");
      }
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", id);
      data.put("status", "DONE");
      data.put("message", "举报已处理");
      return Result.success(data);
    } catch (Exception e) {
      log.error("处理商品举报失败", e);
      return Result.error("处理失败: " + e.getMessage());
    }
  }

  /** 实体行 → 前端展示对象 */
  private Map<String, Object> toVO(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", rs.getLong("id"));
    item.put("productId", rs.getObject("product_id"));
    item.put("productName", rs.getString("product_name"));

    String reportType = rs.getString("report_type");
    String[] typeMeta = reportType == null ? null : TYPE_META.get(reportType);
    item.put("type", typeMeta == null ? reportType : typeMeta[0]);
    item.put("typeClass", typeMeta == null ? "other" : typeMeta[1]);

    item.put("reason", rs.getString("reason"));
    item.put("reporter", rs.getString("reporter"));

    String status = rs.getString("status");
    String[] statusMeta = status == null ? null : STATUS_META.get(status);
    item.put("status", status);
    item.put("statusLabel", statusMeta == null ? status : statusMeta[0]);
    item.put("statusClass", statusMeta == null ? "pending" : statusMeta[1]);

    item.put("suggestion", rs.getString("handle_comment"));
    item.put("handleResult", rs.getString("handle_result"));

    Timestamp createTime = rs.getTimestamp("create_time");
    item.put("time", createTime == null ? null : createTime.toLocalDateTime().format(TIME_FMT));

    // 仅未完成的举报展示 SLA 倒计时
    Timestamp deadline = rs.getTimestamp("sla_deadline");
    String sla = null;
    if (deadline != null && !"DONE".equals(status)) {
      long minutes = Duration.between(LocalDateTime.now(), deadline.toLocalDateTime()).toMinutes();
      sla = minutes < 0 ? "已超时" : "剩余 " + (minutes / 60) + "h " + (minutes % 60) + "m";
    }
    item.put("sla", sla);
    return item;
  }
}
