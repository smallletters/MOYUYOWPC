package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.common.config.YanWenProperties;
import com.moyuyo.common.dto.admin.order.*;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import com.moyuyo.service.admin.AdminOrderOpsService;
import com.moyuyo.service.admin.YanWenCountryDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 订单运营")
@Slf4j
@RestController
@RequestMapping("/api/admin/order-ops")
@RequiredArgsConstructor
public class AdminOrderOpsController {

  private final AdminOrderOpsService adminOrderOpsService;
  private final YanWenProperties yanWenProperties;
  private final JdbcTemplate jdbcTemplate;
  // 燕文国家目录管理：admin 调试接口使用（status / list / refresh / preview）
  private final YanWenCountryDirectory yanWenCountryDirectory;
  // 燕文国家目录查询接口（preview 接口里复用，与 CountryResolver 用同一个 bean）
  private final com.moyuyo.common.util.CountryDirectoryLookup countryDirectoryLookup;
  // yml aliases 配置：preview 接口里做命中检测
  private final com.moyuyo.common.config.CountryMappingProperties countryMappingProperties;
  // CountryResolver 实例：preview 接口里直接 resolve（确保与生产路径一致）
  private final com.moyuyo.common.util.CountryResolver countryResolver;

  @Operation(summary = "订单导出列表")
  @GetMapping("/export")
  public Result<?> exportList(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listExport(status, page, size));
  }

  @Operation(summary = "订单运营统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminOrderOpsService.stats());
  }

  @Operation(summary = "创建导出任务")
  @PostMapping("/export/create")
  @AdminAudit(action = "CREATE", module = "ORDER",
      resourceId = "export-task", detail = "管理员创建订单导出任务")
  public Result<Map<String, Object>> createExport(@RequestBody OrderExportCreateRequest request) {
    // Service 层已切换为接收 OrderExportCreateRequest,直接透传 DTO,避免 Map 类型漂移
    Map<String, Object> taskResult = adminOrderOpsService.createExportTask(request);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", taskResult.getOrDefault("taskId", ""));
    result.put("taskName", request.getTaskName());
    result.put("orderScope", request.getOrderScope());
    result.put("format", request.getFormat() == null ? "Excel" : request.getFormat());
    // 自定义范围回显（仅自定义时有意义，前端按需展示）
    result.put("startDate", request.getStartDate());
    result.put("endDate", request.getEndDate());
    result.put("status", taskResult.getOrDefault("status", "PENDING"));
    result.put("message", "导出任务已创建");
    return Result.success(result);
  }

  /**
   * @deprecated 该路径已与 /export/file/{exportId} 不一致（仅返回元数据，
   *   不是真实下载流）。前端应直接调 /export/file/{exportId}。
   *   保留这里仅为兼容未知调用方，下个迭代可删除。
   */
  @Deprecated
  @Operation(summary = "下载导出文件（已废弃，请使用 /export/file/{exportId}）")
  @GetMapping("/export/download/{exportId}")
  public Result<Map<String, Object>> downloadExport(@PathVariable String exportId) {
    // 仅作元数据返回（兼容历史调用方）；新代码应直接走 /export/file/{exportId} 取真实字节流
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("exportId", exportId);
    // 引导调用方改用统一的下载路径
    result.put("downloadUrl", "/api/admin/order-ops/export/file/" + exportId);
    result.put("message", "请改用 /api/admin/order-ops/export/file/{exportId} 下载真实文件");
    result.put("status", "READY");
    return Result.success(result);
  }

  @Operation(summary = "导出文件内容（流式下载 CSV）")
  @GetMapping(value = "/export/file/{exportId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<byte[]> exportFile(@PathVariable String exportId) {
    // 生成真实 CSV 文件内容（带 BOM，保证 Excel 打开中文不乱码）
    byte[] content = adminOrderOpsService.buildExportFile(exportId);
    String filename = "order-export-" + exportId + ".csv";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename(filename, StandardCharsets.UTF_8).build());
    return ResponseEntity.ok().headers(headers).body(content);
  }

  @Operation(summary = "批量发货")
  @PostMapping("/batch-ship")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "batch-ship", detail = "管理员批量发货")
  public Result<Map<String, Object>> batchShip(@Valid @RequestBody BatchShipRequest request) {
    // 参数校验:ids 已由 @NotEmpty 保证非空
    if (request.getIds() == null || request.getIds().isEmpty()) {
      return Result.error(400, "参数错误：ids 不能为空");
    }
    List<Long> ids = request.getIds();
    // 未指定承运商和运单号时使用默认值(与原 Map 逻辑保持一致)
    String carrier = request.getCarrier() != null ? request.getCarrier() : "默认承运商";
    String trackingNo = request.getTrackingNo() != null ? request.getTrackingNo() : "";
    adminOrderOpsService.batchShip(ids, carrier, trackingNo);
    return Result.success(Map.of("message", "批量发货成功", "count", ids.size()));
  }

  @Operation(summary = "更新备注")
  @PutMapping("/{id}/remark")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员更新订单备注")
  public Result<Map<String, Object>> updateRemark(@PathVariable Long id, @RequestBody OrderRemarkUpdateRequest request) {
    String remark = request != null ? request.getRemark() : null;
    adminOrderOpsService.updateRemark(id, remark);
    return Result.success(Map.of("id", id, "message", "备注更新成功"));
  }

  // ==================== 订单打印 ====================

  @Operation(summary = "订单打印列表")
  @GetMapping("/print/list")
  public Result<?> printList(
      @RequestParam(required = false) String printType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // printType 过滤参数白名单：
    //   - null / '' / 'order'：前端默认兜底值，返回全部待打印订单（不过滤）
    //   - PICK / PACK / SHIP / LABEL / SHIPPING_LABEL：按"曾用此类型打印"过滤
    // 其他值忽略（按不过滤处理），避免脏数据触发慢 SQL EXISTS 子查询
    String effectivePrintType = (printType == null || printType.isBlank() || "order".equals(printType))
        ? null : printType;
    return Result.success(adminOrderOpsService.listPrint(effectivePrintType, page, size));
  }

  /**
   * 允许的 printType 白名单。
   * <p>
   * 与 mo_print_template.code 字段保持一致：PICK / PACK / SHIP / LABEL / SHIPPING_LABEL。
   * 前端传任意字符串都会被拒绝，防止脏数据落库（之前 line 144 直接用 request.getPrintType() 兜底"默认值 PICK"）。
   */
  private static final java.util.Set<String> ALLOWED_PRINT_TYPES = java.util.Set.of(
      "PICK", "PACK", "SHIP", "LABEL", "SHIPPING_LABEL");

  @Operation(summary = "记录打印操作")
  @PostMapping("/print/record")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "print-record", detail = "管理员记录订单打印操作")
  public Result<Map<String, Object>> recordPrint(@Valid @RequestBody OrderPrintRecordRequest request) {
    // @Valid 让 DTO 上的 @NotNull 注解生效（Spring 会抛 MethodArgumentNotValidException）。
    // 显式判空保留作为双保险（@RequestBody 可缺省、整体 null 不会被 @Valid 拦到）。
    if (request == null) {
      return Result.error(400, "参数错误：请求体不能为空");
    }
    if (request.getOrderId() == null) {
      return Result.error(400, "参数错误：orderId 不能为空");
    }
    Long orderId = request.getOrderId();
    // P2：printType 白名单校验 —— 防止前端传任意字符串写入数据库。
    // 若 printType 为空/null，默认用 PICK（拣货单），与历史行为兼容。
    String printType = request.getPrintType() != null ? request.getPrintType() : "PICK";
    if (!ALLOWED_PRINT_TYPES.contains(printType)) {
      return Result.error(400, "printType 不合法：" + printType
          + "（允许值：" + ALLOWED_PRINT_TYPES + "）");
    }
    String templateName = request.getTemplateName() != null ? request.getTemplateName() : "默认模板";
    // paperSize 归一化交给 Service.normalizePaperSize() 处理（白名单字典映射，
    // 不能简单 toUpperCase 否则 'thermal-100' 会变成 'THERMAL-100' 与模板表不一致）
    String paperSize = request.getPaperSize();
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
    adminOrderOpsService.recordPrint(orderId, printType, templateName, paperSize, operator);
    return Result.success(Map.of("message", "打印记录成功"));
  }

  /**
   * 单独累加一次燕文电子面单打印日志（由前端在用户实际点击打印按钮后调用，
   * 不再由 fetchShippingLabel 自动触发，避免预览动作被误记为打印）。
   */
  @Operation(summary = "累加燕文面单打印日志")
  @PostMapping("/print/shipping-label/record/{orderId}")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#orderId", detail = "管理员累加燕文面单打印日志")
  public Result<Map<String, Object>> recordShippingLabelLog(@PathVariable Long orderId) {
    if (orderId == null) {
      return Result.error(400, "参数错误：orderId 不能为空");
    }
    adminOrderOpsService.recordShippingLabelLog(orderId);
    return Result.success(Map.of("orderId", orderId, "message", "燕文面单日志已记录"));
  }

  /**
   * 取单个订单的打印详情（收货人/商品/承运商），用于前端打印预览页 OrderPrint.vue。
   * <p>
   * 与 /print/list 的差异：本接口返回单条订单的完整结构（items 商品明细），便于前端
   * 渲染拣货单 / 发货单 / 配货标签 / 快递面单多种模板。
   */
  @Operation(summary = "单个订单打印详情")
  @GetMapping("/print/detail/{orderId}")
  public Result<Map<String, Object>> printDetail(@PathVariable Long orderId) {
    Map<String, Object> detail = adminOrderOpsService.getPrintDetail(orderId);
    if (detail == null) {
      return Result.error(404, "订单不存在或已被删除");
    }
    return Result.success(detail);
  }

  /**
   * 取订单的快递电子面单（按 carrier.code 路由 SDK，目前支持 yanwen）。
   * <p>
   * 调用方应当先确保订单已录入运单号 + 承运商编码 = yanwen；
   * SDK 启用开关参见 {@code moyuyo.logistics.yanwen.enabled}。
   */
  @Operation(summary = "取订单快递面单（燕文电子面单）")
  @GetMapping("/print/shipping-label")
  public Result<Map<String, Object>> shippingLabel(
      @RequestParam Long orderId,
      @RequestParam(required = false) Long carrierId) {
    if (!yanWenProperties.isEnabled()) {
      return Result.error(503, "燕文电子面单未启用，请先在 moyuyo.logistics.yanwen.enabled 设置为 true 并配置凭证");
    }
    try {
      YanWenLabelResponse resp = adminOrderOpsService.fetchShippingLabel(orderId, carrierId);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("waybillNumber", resp.getWaybillNumber());
      data.put("contentType", resp.getContentType());
      data.put("sizeBytes", resp.getSizeBytes());
      // P1-2：不再在响应里拼装 "data:...;base64,..." 这种超长 dataUrl。
      //   - 旧实现会让单个面单响应体暴增 33%（base64 编码膨胀 + data URL 前缀）
      //   - 批量 5 张 → 1~2MB 的 JSON，Spring 默认 Jackson 序列化 + gzip 之前就已经很大
      //   - 前端拿到 base64String 后自己拼 data URL 即可（atob + btoa 都不需要，纯字符串拼接）
      data.put("base64String", resp.getBase64String());
      data.put("message", "面单获取成功，请使用浏览器打印");
      return Result.success(data);
    } catch (Exception e) {
      log.warn("取燕文面单失败：orderId={}, err={}", orderId, e.getMessage());
      return Result.error(500, "取面单失败：" + e.getMessage());
    }
  }

  /**
   * 燕文 SDK 状态（前端按钮"打印快递单"是否可点的判断依据）。
   */
  @Operation(summary = "燕文电子面单启用状态")
  @GetMapping("/print/shipping-status")
  public Result<Map<String, Object>> yanwenStatus() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("enabled", yanWenProperties.isEnabled());
    data.put("userIdConfigured", yanWenProperties.getUserId() != null && !yanWenProperties.getUserId().isBlank());
    data.put("apiTokenConfigured", yanWenProperties.getApiToken() != null && !yanWenProperties.getApiToken().isBlank());
    return Result.success(data);
  }

  /**
   * P0（路径B）：仅创建燕文运单（不取面单）。
   * <p>
   * 适用场景：
   *   - 提前创建运单（运营先核对地址，再走 /print/shipping-label 取面单）
   *   - 调试 createOrder 请求/响应字段
   *   - 想拿 waybillNumber 但暂不打面单
   * <p>
   * 成功后：燕文返回的运单号已写回 mo_order.tracking_number + mo_order.shipping_carrier，
   * 后续取面单走"已运单号"快路径。
   */
  @Operation(summary = "创建燕文运单（路径B：先建运单，再取面单）")
  @PostMapping("/print/shipping-label/create-waybill")
  @AdminAudit(action = "CREATE", module = "ORDER",
      resourceId = "#orderId", detail = "管理员创建燕文运单")
  public Result<Map<String, Object>> createYanwenWaybill(
      @RequestParam Long orderId,
      @RequestParam(required = false) Long carrierId) {
    if (orderId == null) {
      return Result.error(400, "orderId 不能为空");
    }
    if (!yanWenProperties.isEnabled()) {
      return Result.error(503, "燕文电子面单未启用，请先在 moyuyo.logistics.yanwen.enabled 设置为 true 并配置凭证");
    }
    try {
      String waybill = adminOrderOpsService.createYanwenWaybill(orderId, carrierId);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("orderId", orderId);
      data.put("waybillNumber", waybill);
      data.put("message", "燕文运单创建成功，已写回订单。可继续调用 /print/shipping-label 取面单。");
      return Result.success(data);
    } catch (Exception e) {
      log.warn("创建燕文运单失败：orderId={}, err={}", orderId, e.getMessage());
      return Result.error(500, "创建燕文运单失败：" + e.getMessage());
    }
  }

  // ==================== 打印模板 CRUD ====================
  // 对应 mo_print_template 表（Flyway: V20260929_03__create_mo_print_template.sql）
  // 解决 OrderPrint.vue 编辑模板"刷新即丢失"问题：模板数据由数据库持久化。

  @Operation(summary = "查询所有打印模板")
  @GetMapping("/print/templates")
  public Result<List<Map<String, Object>>> listPrintTemplates() {
    return Result.success(adminOrderOpsService.listPrintTemplates());
  }

  // ==================== 燕文国家目录（admin 调试用） ====================
  // 提供 4 个端点帮运营排查"地址解析出的国家码对不对 / 缓存是否最新"。
  // 设计目标：让运营不需要登服务器、不需要看日志就能验证 CountryResolver 行为。

  /**
   * 国家目录缓存状态。
   * <p>
   * 包含：是否启用、缓存大小、上次成功刷新时间、上次刷新耗时、上次刷新错误。
   * 用于运营检查"燕文缓存是否工作"。
   */
  @Operation(summary = "燕文国家目录缓存状态")
  @GetMapping("/print/yanwen-countries/status")
  public Result<Map<String, Object>> yanwenCountryDirectoryStatus() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("yanwenEnabled", yanWenProperties.isEnabled());
    data.put("cacheSize", yanWenCountryDirectory.size());
    // P0：返回毫秒时间戳而非 Date 对象 —— 避免 Jackson 默认 ISO 字符串化后
    // 前端 new Date("...Z") 解析再 getHours() 时受时区漂移影响（UTC vs 本地时区）
    long ts = yanWenCountryDirectory.getLastRefreshAt();
    data.put("lastRefreshAt", ts == 0 ? null : ts);
    data.put("lastRefreshCostMs", yanWenCountryDirectory.getLastRefreshCostMs());
    data.put("lastRefreshError", yanWenCountryDirectory.getLastRefreshError());
    data.put("refreshScheduleHours", 24);
    return Result.success(data);
  }

  /**
   * 国家目录完整列表（带搜索）。
   * <p>
   * 搜索规则：在缓存快照里找"小写国名（含小写 code）contains 小写 keyword"。
   * 返回结构 [{code, name}] —— 前端用于"国家选择器"或排查"某个国名为啥没匹配"。
   * <p>
   * 这里返回的 code / name 都是燕文原始字段，未做大小写归一（运营排查时要看到原始值）。
   */
  @Operation(summary = "燕文国家目录列表（支持 keyword 模糊搜索）")
  @GetMapping("/print/yanwen-countries")
  public Result<List<Map<String, Object>>> yanwenCountryDirectoryList(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "500") int limit) {
    Map<String, String> snapshot = yanWenCountryDirectory.snapshot();
    String kw = keyword == null ? null : keyword.trim().toLowerCase();
    List<Map<String, Object>> out = new ArrayList<>();
    int count = 0;
    // 用 snapshot 反向归一：key 是小写国名 → value 是 code
    // 反推回原始大写需要后端保留原始数据 —— 这里用 LinkedHashMap 兜底
    // 设计取舍：缓存 map 的 key 是小写国名 / value 是 code，原始 nameCh / nameEn 没保留
    //   折中：返回 {code, name（= 小写 key 转回首字母大写）}  —— 仅作调试够用
    for (Map.Entry<String, String> e : snapshot.entrySet()) {
      if (kw != null && !e.getKey().contains(kw) && !e.getValue().toLowerCase().contains(kw)) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", e.getValue());
      item.put("name", e.getKey()); // 小写归一后值；运营可通过首字母大写 + code 校验
      out.add(item);
      count++;
      if (count >= limit) break;
    }
    return Result.success(out);
  }

  /**
   * 主动触发一次燕文国家目录刷新（不阻塞调用线程）。
   * <p>
   * 用于：① 运营改了 yml 后想立刻生效；② 燕文新增国家后想立即看到；
   * ③ 调试"上次刷新失败"后手动重试。
   * <p>
   * 注意：本接口只触发刷新动作，不等待结果。如要看结果请调 status 接口。
   */
  @Operation(summary = "主动刷新燕文国家目录")
  @PostMapping("/print/yanwen-countries/refresh")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "yanwen-refresh", detail = "管理员主动刷新燕文国家目录")
  public Result<Map<String, Object>> yanwenCountryDirectoryRefresh() {
    if (!yanWenProperties.isEnabled()) {
      return Result.error(503, "燕文未启用，无法刷新");
    }
    yanWenCountryDirectory.refreshNow();
    return Result.success(Map.of(
            "message", "已提交刷新任务，几秒后查询 status 接口查看结果",
            "cacheSizeBefore", yanWenCountryDirectory.size()));
  }

  /**
   * 解析预览：输入 address，返回 CountryResolver 解析出的国家码 + 命中规则。
   * <p>
   * 用于运营排查"这个地址为啥解析成 X 国家 / 命中的是哪条规则"。
   * 返回结构：
   *   - resolvedCountry: 解析结果
   *   - hitDirectoryLookup: 是否命中燕文目录缓存
   *   - hitAliasesPattern: yml aliases 命中的 pattern
   *   - hitRule: 命中规则描述
   * <p>
   * 调试专用，前端可作为"高级运营面板"的功能。
   */
  @Operation(summary = "地址解析预览（CountryResolver 调试）")
  @PostMapping("/print/yanwen-countries/preview")
  public Result<Map<String, Object>> yanwenCountryPreview(@RequestBody Map<String, String> body) {
    if (body == null) body = new java.util.HashMap<>();
    String address = body.get("address");
    if (address == null || address.isBlank()) {
      return Result.error(400, "address 不能为空");
    }
    Map<String, Object> data = new LinkedHashMap<>();
    String resolved = countryResolver.resolve(address);
    data.put("address", address);
    data.put("resolvedCountry", resolved);

    // P0：精确检测每层命中 —— 与 CountryResolver 内部优先级一致（目录 > aliases > 兜底）
    //   1) 先查目录缓存 —— 如果非空且与 resolvedCountry 一致 → directoryLookupHit 返 code
    //   2) 否则查 aliases —— 如果命中 → aliasHit = true + aliasPattern
    //   3) 否则就是 default-country 兜底
    // 这样避免运营看到"目录命中 + aliases 命中"的双标签造成的混淆。
    String dirHit = null;
    boolean directoryActive = false;
    try {
      if (countryDirectoryLookup != null && countryDirectoryLookup.size() > 0) {
        directoryActive = true;
        String found = countryDirectoryLookup.findFirstCodeIn(address);
        // 仅当目录命中结果与生产路径 resolvedCountry 一致，才算"目录路径命中"
        if (found != null && found.equalsIgnoreCase(resolved)) {
          dirHit = found;
        }
      }
    } catch (Exception ignored) {}
    data.put("directoryLookupHit", dirHit);
    data.put("directoryActive", directoryActive);

    boolean aliasHit = false;
    String aliasPattern = null;
    String defaultCountry = countryMappingProperties != null
            ? countryMappingProperties.getDefaultCountry() : "US";
    // aliases 仅在目录**未命中**时才参与判断（与 CountryResolver 内部逻辑一致）
    if (dirHit == null && countryMappingProperties != null
            && countryMappingProperties.getAliases() != null) {
      for (var rule : countryMappingProperties.getAliases()) {
        if (rule == null || rule.getPattern() == null || rule.getCountry() == null) continue;
        String p = rule.getPattern();
        String target = address;
        if (containsRegexMeta(p)) {
          // 邮编正则：从 address 抽邮编段
          String zip = extractFirstZipLike(address);
          if (zip != null) target = zip;
          if (java.util.regex.Pattern.compile(p).matcher(target).matches()) {
            aliasHit = true;
            aliasPattern = p + " → " + rule.getCountry();
            break;
          }
        } else {
          if (address.toLowerCase().contains(p.toLowerCase())) {
            aliasHit = true;
            aliasPattern = p + " → " + rule.getCountry();
            break;
          }
        }
      }
    }
    data.put("aliasHit", aliasHit);
    data.put("aliasPattern", aliasPattern);
    data.put("defaultCountry", defaultCountry);
    // 综合路径（前端运营一眼能看出最终命中哪一层）
    String hitPath;
    if (dirHit != null) {
      hitPath = "directory";
    } else if (aliasHit) {
      hitPath = "alias";
    } else {
      hitPath = "default";
    }
    data.put("hitPath", hitPath);
    return Result.success(data);
  }

  // ==================== 打印设置（服务端持久化） ====================
  // P1：替代 localStorage，多设备/多浏览器共享同一份打印设置。
  // 复用 mo_system_config（key/value 结构，无需新建表）。

  @Operation(summary = "获取订单打印设置")
  @GetMapping("/print/settings")
  public Result<Map<String, Object>> getPrintSettings() {
    return Result.success(adminOrderOpsService.getPrintSettings());
  }

  @Operation(summary = "保存订单打印设置")
  @PutMapping("/print/settings")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "print-settings", detail = "管理员保存订单打印设置")
  public Result<Map<String, Object>> savePrintSettings(@RequestBody Map<String, Object> body) {
    adminOrderOpsService.savePrintSettings(body);
    return Result.success(Map.of("message", "打印设置已保存到服务端"));
  }

  @Operation(summary = "更新打印模板")
  @PutMapping("/print/templates/{id}")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员更新打印模板")
  public Result<Map<String, Object>> updatePrintTemplate(
      @PathVariable Long id, @Valid @RequestBody PrintTemplateRequest request) {
    adminOrderOpsService.updatePrintTemplate(
        id, request.getName(), request.getPaperSize(), request.getDescription(), request.getContentTemplate(),
        request.getIsDefault(), request.getSortOrder());
    return Result.success(Map.of("id", id, "message", "模板已更新"));
  }

  @Operation(summary = "设置默认打印模板")
  @PutMapping("/print/templates/{id}/set-default")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员设置默认打印模板")
  public Result<Map<String, Object>> setDefaultPrintTemplate(@PathVariable Long id) {
    adminOrderOpsService.setDefaultPrintTemplate(id);
    return Result.success(Map.of("id", id, "message", "已设为默认模板"));
  }

  // ==================== 订单改价 ====================

  @Operation(summary = "改价记录列表")
  @GetMapping("/price-modify/list")
  public Result<?> priceModifyList(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long orderId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listPriceModify(keyword, orderId, page, size));
  }

  @Operation(summary = "创建改价记录")
  @PostMapping("/price-modify/create")
  @AdminAudit(action = "CREATE", module = "ORDER",
      resourceId = "#request.orderId", detail = "管理员创建订单改价记录")
  public Result<Map<String, Object>> createPriceModify(@RequestBody OrderPriceModifyRequest request) {
    // 参数校验:必须提供 orderId 或 orderNo
    Long orderId = request.getOrderId();
    String orderNo = request.getOrderNo();
    if (orderId == null && (orderNo == null || orderNo.isEmpty())) {
      return Result.error(400, "参数错误：orderId 或 orderNo 不能为空");
    }
    // 参数校验:adjustAmount 必填
    if (request.getAdjustAmount() == null) {
      return Result.error(400, "参数错误：adjustAmount 不能为空");
    }
    java.math.BigDecimal adjustAmount = request.getAdjustAmount();
    java.math.BigDecimal originalAmount = request.getOriginalAmount();
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String reason = request.getReason() != null ? request.getReason() : "";
    String reasonType = request.getReasonType() != null ? request.getReasonType() : "MANUAL";
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
    adminOrderOpsService.createPriceModify(orderId, orderNo, originalAmount, adjustAmount, reason, reasonType, operator);
    return Result.success(Map.of("message", "改价成功"));
  }

  // ==================== 订单拦截 ====================

  @Operation(summary = "拦截记录列表")
  @GetMapping("/intercept/list")
  public Result<?> interceptList(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listIntercept(status, page, size));
  }

  @Operation(summary = "创建拦截")
  @PostMapping("/intercept/create")
  @AdminAudit(action = "CREATE", module = "ORDER",
      resourceId = "#request.orderId", detail = "管理员创建订单拦截")
  public Result<Map<String, Object>> createIntercept(@RequestBody OrderInterceptRequest request) {
    // 参数校验:orderId 必填,避免 NPE 导致 500
    if (request.getOrderId() == null) {
      return Result.error(400, "参数错误：orderId 不能为空");
    }
    Long orderId = request.getOrderId();
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String interceptType = request.getInterceptType() != null ? request.getInterceptType() : "MANUAL";
    String reason = request.getReason() != null ? request.getReason() : "";
    String reasonTemplate = request.getReasonTemplate() != null ? request.getReasonTemplate() : "";
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
    adminOrderOpsService.createIntercept(orderId, interceptType, reason, reasonTemplate, operator);
    return Result.success(Map.of("message", "订单已拦截"));
  }

  @Operation(summary = "解除拦截")
  @PostMapping("/intercept/release/{id}")
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员解除订单拦截")
  public Result<Map<String, Object>> releaseIntercept(@PathVariable Long id, @RequestBody(required = false) OrderInterceptReleaseRequest request) {
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String releaseReason = request != null && request.getReleaseReason() != null
        ? request.getReleaseReason() : "";
    String releaseOperator = request != null && request.getReleaseOperator() != null
        ? request.getReleaseOperator() : "系统";
    adminOrderOpsService.releaseIntercept(id, releaseReason, releaseOperator);
    return Result.success(Map.of("message", "拦截已解除"));
  }

  // ==================== 订单监控 ====================

  // ==================== 订单时间轴 ====================
  // P2：聚合订单的全生命周期事件流（创建/支付/发货/收货/物流/拦截/改价/打印），
  //   用于 OrderDetail.vue 的"全景时间轴"展示。
  // 单一接口替代前端多次轮询 5 张表。

  @Operation(summary = "订单时间轴（按时间升序的事件流）")
  @GetMapping("/orders/{orderId}/timeline")
  public Result<Map<String, Object>> orderTimeline(
      @PathVariable Long orderId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset) {
    if (orderId == null) {
      return Result.error(400, "orderId 不能为空");
    }
    // UNION ALL SQL 第一条就是 mo_order WHERE id = ?，订单不存在时整段返回空数组，
    //   直接判 404 即可（无需再查一次 order 存在性）。
    // limit/offset 用于分页；前端按"加载更多"模式拼 offset/length。
    // 返回结构：{ events: [...], total: N, limit, offset }，方便前端判断是否还有更多。
    Map<String, Object> timeline = adminOrderOpsService.getOrderTimeline(orderId, limit, offset);
    if (((java.util.List<?>) timeline.get("events")).isEmpty()) {
      return Result.error(404, "订单不存在");
    }
    return Result.success(timeline);
  }

  @Operation(summary = "异常订单监控看板")
  @GetMapping("/monitor/data")
  public Result<Map<String, Object>> monitorData() {
    return Result.success(adminOrderOpsService.getMonitorData());
  }

  @Operation(summary = "异常订单列表")
  @GetMapping("/monitor/list")
  public Result<?> abnormalOrders(
      @RequestParam(required = false) String abnormalType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listAbnormalOrders(abnormalType, page, size));
  }

  // ==================== 订单监控规则 CRUD ====================
  // 对应 mo_order_monitor_rule 表，由 V20260804_01 迁移创建
  // 直接使用 JdbcTemplate 操作数据库，避免在 api 模块新增 mapper/entity 依赖

  @Operation(summary = "监控规则列表")
  @GetMapping("/monitor/rules")
  public Result<List<Map<String, Object>>> monitorRules() {
    // 查询所有规则，按优先级升序、ID 升序
    String sql = "SELECT id, name, condition_text, action_type, enabled, priority, creator, " +
                 "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                 "DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time " +
                 "FROM mo_order_monitor_rule ORDER BY priority ASC, id ASC";
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
      // 统一将 enabled 字段规范化为 boolean（jdbc 返回 Integer 0/1）
      List<Map<String, Object>> result = new ArrayList<>(rows.size());
      for (Map<String, Object> row : rows) {
        Map<String, Object> item = new LinkedHashMap<>(row);
        Object enabled = row.get("enabled");
        if (enabled instanceof Number) {
          item.put("enabled", ((Number) enabled).intValue() == 1);
        }
        result.add(item);
      }
      return Result.success(result);
    } catch (Exception e) {
      log.warn("查询监控规则失败，返回空列表：{}", e.getMessage());
      return Result.success(new ArrayList<>());
    }
  }

  @Operation(summary = "创建监控规则")
  @PostMapping("/monitor/rules")
  @Transactional
  @AdminAudit(action = "CREATE", module = "ORDER",
      resourceId = "monitor-rule", detail = "管理员创建订单监控规则")
  public Result<Map<String, Object>> createMonitorRule(@RequestBody Map<String, Object> body) {
    String name = body.get("name") != null ? body.get("name").toString().trim() : "";
    String condition = body.get("condition") != null ? body.get("condition").toString().trim() : "";
    String action = body.get("action") != null ? body.get("action").toString() : "FLAG";
    boolean enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"));
    Integer priority = body.get("priority") != null ? Integer.valueOf(body.get("priority").toString()) : 100;
    String creator = body.get("creator") != null ? body.get("creator").toString() : "系统";

    if (name.isEmpty()) {
      return Result.error(400, "规则名称不能为空");
    }
    if (condition.isEmpty()) {
      return Result.error(400, "触发条件不能为空");
    }
    // 限制 action 取值
    if (!List.of("FLAG", "INTERCEPT", "CANCEL").contains(action)) {
      return Result.error(400, "动作类型非法，应为 FLAG/INTERCEPT/CANCEL");
    }

    String sql = "INSERT INTO mo_order_monitor_rule(name, condition_text, action_type, enabled, priority, creator, create_time, update_time) " +
                 "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
    try {
      jdbcTemplate.update(sql, name, condition, action, enabled ? 1 : 0, priority, creator);
      Long newId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", newId);
      result.put("message", "规则已创建");
      return Result.success(result);
    } catch (Exception e) {
      log.error("创建监控规则失败", e);
      return Result.error(500, "创建监控规则失败：" + e.getMessage());
    }
  }

  @Operation(summary = "更新监控规则")
  @PutMapping("/monitor/rules/{id}")
  @Transactional
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员更新订单监控规则")
  public Result<Map<String, Object>> updateMonitorRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String name = body.get("name") != null ? body.get("name").toString().trim() : "";
    String condition = body.get("condition") != null ? body.get("condition").toString().trim() : "";
    String action = body.get("action") != null ? body.get("action").toString() : "FLAG";
    boolean enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"));
    Integer priority = body.get("priority") != null ? Integer.valueOf(body.get("priority").toString()) : 100;

    if (name.isEmpty() || condition.isEmpty()) {
      return Result.error(400, "规则名称和触发条件不能为空");
    }
    if (!List.of("FLAG", "INTERCEPT", "CANCEL").contains(action)) {
      return Result.error(400, "动作类型非法");
    }

    String sql = "UPDATE mo_order_monitor_rule SET name = ?, condition_text = ?, action_type = ?, " +
                 "enabled = ?, priority = ?, update_time = NOW() WHERE id = ?";
    try {
      int affected = jdbcTemplate.update(sql, name, condition, action, enabled ? 1 : 0, priority, id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "message", "规则已更新"));
    } catch (Exception e) {
      log.error("更新监控规则失败", e);
      return Result.error(500, "更新监控规则失败：" + e.getMessage());
    }
  }

  @Operation(summary = "切换监控规则启用状态")
  @PutMapping("/monitor/rules/{id}/status")
  @Transactional
  @AdminAudit(action = "UPDATE", module = "ORDER",
      resourceId = "#id", detail = "管理员切换订单监控规则启用状态")
  public Result<Map<String, Object>> toggleMonitorRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Object enabledObj = body.get("enabled");
    if (enabledObj == null) {
      return Result.error(400, "缺少 enabled 参数");
    }
    boolean enabled = Boolean.TRUE.equals(enabledObj);
    String sql = "UPDATE mo_order_monitor_rule SET enabled = ?, update_time = NOW() WHERE id = ?";
    try {
      int affected = jdbcTemplate.update(sql, enabled ? 1 : 0, id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "enabled", enabled, "message", enabled ? "规则已启用" : "规则已停用"));
    } catch (Exception e) {
      log.error("切换监控规则状态失败", e);
      return Result.error(500, "切换规则状态失败：" + e.getMessage());
    }
  }

  @Operation(summary = "删除监控规则")
  @DeleteMapping("/monitor/rules/{id}")
  @Transactional
  @AdminAudit(action = "DELETE", module = "ORDER",
      resourceId = "#id", detail = "管理员删除订单监控规则")
  public Result<Map<String, Object>> deleteMonitorRule(@PathVariable Long id) {
    try {
      int affected = jdbcTemplate.update("DELETE FROM mo_order_monitor_rule WHERE id = ?", id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "message", "规则已删除"));
    } catch (EmptyResultDataAccessException e) {
      return Result.error(404, "规则不存在");
    } catch (Exception e) {
      log.error("删除监控规则失败", e);
      return Result.error(500, "删除监控规则失败：" + e.getMessage());
    }
  }

  // ==================== 燕文国家目录调试辅助方法 ====================

  /**
   * 启发式判定 pattern 是否为正则（与 CountryResolver 内部逻辑一致）。
   */
  private static boolean containsRegexMeta(String s) {
    if (s == null || s.length() < 2) return false;
    return s.startsWith("^") || s.endsWith("$")
            || s.contains(".*") || s.contains(".+")
            || s.contains("\\d") || s.contains("\\s")
            || s.contains("[") || s.contains("(");
  }

  /**
   * 简易邮编抽取（与 CountryResolver 内部一致）：取第一个"看起来像邮编"的字段。
   * <p>
   * 这里只做最简实现，覆盖 5/5-4/6/4 位 + 加拿大/英国字母数字混合格式。
   * 注意：与 CountryResolver 的 ZIP_PATTERN 完全等价（保留一份是为了 controller 独立编译）。
   */
  private static String extractFirstZipLike(String address) {
    if (address == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\\b(\\d{5}(-\\d{4})?|\\d{6}|\\d{4}|[A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d|[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b"
    ).matcher(address);
    return m.find() ? m.group(1) : null;
  }
}
