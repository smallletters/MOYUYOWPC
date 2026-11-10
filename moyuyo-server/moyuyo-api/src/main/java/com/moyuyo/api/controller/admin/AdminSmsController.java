package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.dao.admin.entity.SmsTemplateEntity;
import com.moyuyo.dao.admin.mapper.SmsTemplateMapper;
import com.moyuyo.service.admin.AdminSmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Tag(name = "管理后台 - 短信管理")
@RestController
@RequestMapping("/api/admin/sms")
@RequiredArgsConstructor
public class AdminSmsController {

  private final AdminSmsService adminSmsService;
  private final SmsTemplateMapper smsTemplateMapper;

  /** 模板变量占位符：${code} / ${name} */
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

  /** 模板类型：编码 → 中文 */
  private static final Map<String, String> TYPE_TEXT = Map.of(
      "CODE", "验证码",
      "NOTICE", "通知",
      "MARKETING", "营销"
  );

  /** 审核状态：编码 → 中文 */
  private static final Map<String, String> STATUS_TEXT = Map.of(
      "APPROVED", "已通过",
      "PENDING", "待审核",
      "REJECTED", "已驳回"
  );

  @Operation(summary = "短信统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminSmsService.stats());
  }

  @Operation(summary = "发送记录列表")
  @GetMapping("/records")
  public Result<?> records(
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminSmsService.listAll(phone, status, page, size));
  }

  @Operation(summary = "发送短信")
  @PostMapping("/send")
  @AdminAudit(action = "CREATE", module = "MARKETING",
      resourceId = "sms-send", detail = "管理员发送短信")
  public Result<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
    String phone = (String) body.get("phone");
    String content = (String) body.get("content");
    return Result.success(adminSmsService.send(phone, content));
  }

  @Operation(summary = "短信模板列表")
  @GetMapping("/templates")
  public Result<List<Map<String, Object>>> templates() {
    List<SmsTemplateEntity> list = smsTemplateMapper.selectList(
        new LambdaQueryWrapper<SmsTemplateEntity>().orderByDesc(SmsTemplateEntity::getCreateTime));
    List<Map<String, Object>> result = new ArrayList<>();
    for (SmsTemplateEntity entity : list) {
      result.add(toTemplateVO(entity));
    }
    return Result.success(result);
  }

  @Operation(summary = "新建短信模板")
  @PostMapping("/templates/create")
  @AdminAudit(action = "CREATE", module = "MARKETING",
      resourceId = "#body.id", detail = "管理员新建短信模板")
  public Result<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    String content = (String) body.get("content");
    if (!StringUtils.hasText(name) || !StringUtils.hasText(content)) {
      return Result.error(400, "参数错误：模板名称与内容不能为空");
    }
    SmsTemplateEntity entity = new SmsTemplateEntity();
    entity.setName(name);
    entity.setType(normalizeType((String) body.get("type")));
    entity.setTemplateId((String) body.get("templateId"));
    entity.setContent(content);
    // 新建模板默认待审核
    entity.setStatus("PENDING");
    entity.setEnabled(1);
    smsTemplateMapper.insert(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("message", "模板已创建");
    return Result.success(result);
  }

  @Operation(summary = "更新短信模板")
  @PutMapping("/templates/{id}")
  @AdminAudit(action = "UPDATE", module = "MARKETING",
      resourceId = "#id", detail = "管理员更新短信模板")
  public Result<Map<String, Object>> updateTemplate(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
    SmsTemplateEntity entity = smsTemplateMapper.selectById(id);
    if (entity == null) {
      return Result.error(404, "模板不存在");
    }
    if (body.get("name") != null) entity.setName(String.valueOf(body.get("name")));
    if (body.get("type") != null) entity.setType(normalizeType(String.valueOf(body.get("type"))));
    if (body.get("templateId") != null) entity.setTemplateId(String.valueOf(body.get("templateId")));
    if (body.get("content") != null) entity.setContent(String.valueOf(body.get("content")));
    if (body.get("rejectReason") != null) entity.setRejectReason(String.valueOf(body.get("rejectReason")));
    if (body.get("status") != null) entity.setStatus(String.valueOf(body.get("status")));
    smsTemplateMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "模板已更新");
    return Result.success(result);
  }

  @Operation(summary = "启用/禁用短信模板")
  @PutMapping("/templates/{id}/toggle")
  @AdminAudit(action = "UPDATE", module = "MARKETING",
      resourceId = "#id", detail = "管理员启用/禁用短信模板")
  public Result<Map<String, Object>> toggleTemplate(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, Object> body) {
    SmsTemplateEntity entity = smsTemplateMapper.selectById(id);
    if (entity == null) {
      return Result.error(404, "模板不存在");
    }
    // 未显式传 enabled 时按当前状态取反
    Integer enabled = null;
    if (body != null && body.get("enabled") != null) {
      enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled"))) ? 1 : 0;
    }
    if (enabled == null) {
      enabled = entity.getEnabled() != null && entity.getEnabled() == 1 ? 0 : 1;
    }
    entity.setEnabled(enabled);
    smsTemplateMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("enabled", enabled);
    result.put("message", enabled == 1 ? "模板已启用" : "模板已禁用");
    return Result.success(result);
  }

  @Operation(summary = "删除短信模板")
  @DeleteMapping("/templates/{id}")
  @AdminAudit(action = "DELETE", module = "MARKETING",
      resourceId = "#id", detail = "管理员删除短信模板")
  public Result<Map<String, Object>> deleteTemplate(@PathVariable Long id) {
    smsTemplateMapper.deleteById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "模板已删除");
    return Result.success(result);
  }

  /** 实体 → 前端展示对象（类型/状态转中文，并解析变量列表） */
  private Map<String, Object> toTemplateVO(SmsTemplateEntity entity) {
    String type = entity.getType();
    String status = entity.getStatus();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", entity.getId());
    item.put("name", entity.getName());
    // Map.of 查询 null 会抛 NPE，这里显式兜底
    item.put("type", type == null ? "通知" : TYPE_TEXT.getOrDefault(type, type));
    item.put("typeCode", type);
    item.put("templateId", entity.getTemplateId());
    item.put("content", entity.getContent());
    item.put("status", status == null ? "待审核" : STATUS_TEXT.getOrDefault(status, status));
    item.put("statusCode", status);
    item.put("rejectReason", entity.getRejectReason());
    item.put("enabled", entity.getEnabled() != null && entity.getEnabled() == 1);
    item.put("vars", extractVars(entity.getContent()));
    item.put("createTime", entity.getCreateTime());
    return item;
  }

  /** 从模板内容解析 ${var} 变量名 */
  private List<String> extractVars(String content) {
    List<String> vars = new ArrayList<>();
    if (content == null) return vars;
    Matcher matcher = VAR_PATTERN.matcher(content);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!vars.contains(name)) vars.add(name);
    }
    return vars;
  }

  /** 类型中文/编码归一化为数据库编码 */
  private String normalizeType(String type) {
    if (type == null || type.isBlank()) return "NOTICE";
    for (Map.Entry<String, String> entry : TYPE_TEXT.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(type) || entry.getValue().equals(type)) {
        return entry.getKey();
      }
    }
    return "NOTICE";
  }
}
