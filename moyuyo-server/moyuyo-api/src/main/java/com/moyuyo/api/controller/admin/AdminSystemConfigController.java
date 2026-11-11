package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.dao.admin.entity.AuditLogEntity;
import com.moyuyo.dao.admin.mapper.AuditLogMapper;
import com.moyuyo.service.admin.SystemConfigService;
import com.moyuyo.service.config.AiCsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 系统配置")
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（Function<T,R> 方法引用）
@SuppressWarnings("null")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    private final AuditLogMapper auditLogMapper;

    private final AiCsConfigService aiCsConfigService;

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> getConfig(@RequestParam(defaultValue = "basic") String group) {
        Map<String, Object> config = systemConfigService.getConfig(group);
        // 将 Map 转换为前端期望的 List 格式
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            // 敏感项（如扣子 token）只返回掩码，避免密钥下发到浏览器
            item.put("value", maskSensitive(entry.getKey(), entry.getValue()));
            item.put("type", "text");
            item.put("label", entry.getKey());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "保存配置")
    @PutMapping("/config")
    @AdminAudit(action = "UPDATE", module = "SYSTEM",
            resourceId = "system-config", detail = "管理员保存系统/积分规则配置")
    public Result<Map<String, Object>> saveConfig(@RequestParam(defaultValue = "basic") String group,
                                                   @RequestBody List<Map<String, Object>> configs) {
        // 将前端传入的 List 转换为 Map
        Map<String, Object> configMap = new LinkedHashMap<>();
        for (Map<String, Object> item : configs) {
            String key = (String) item.get("key");
            Object value = item.get("value");
            // 敏感项回传的是掩码，说明前端未修改，跳过以免把掩码写入数据库
            if (isSensitive(key) && String.valueOf(value).contains(MASK)) {
                continue;
            }
            configMap.put(key, value);
        }
        systemConfigService.saveConfig(group, configMap);
        // 保存了 AI 客服配置：立即失效本地缓存，避免要等 30 秒缓存过期才生效
        if (configMap.keySet().stream().anyMatch(key -> key != null && key.startsWith(AI_CS_PREFIX))) {
            aiCsConfigService.refresh();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", configMap.size());
        result.put("message", "配置保存成功");
        return Result.success(result);
    }

    /** 掩码占位符 */
    private static final String MASK = "****";

    /** 敏感配置项后缀（如 ai_cs_token），读取时脱敏、保存时忽略掩码值 */
    private static final String SENSITIVE_SUFFIX = "_token";

    /** AI 客服配置项前缀（保存后需要刷新配置缓存） */
    private static final String AI_CS_PREFIX = "ai_cs_";

    private boolean isSensitive(String key) {
        return key != null && key.endsWith(SENSITIVE_SUFFIX);
    }

    /**
     * 敏感值脱敏：只保留前 4 位与后 4 位，便于管理员确认是哪把密钥
     */
    private Object maskSensitive(String key, Object value) {
        if (!isSensitive(key) || value == null) {
            return value;
        }
        String text = String.valueOf(value);
        if (text.length() <= 8) {
            return MASK;
        }
        return text.substring(0, 4) + MASK + text.substring(text.length() - 4);
    }

    @Operation(summary = "操作/运营日志")
  @GetMapping("/logs")
  public Result<Map<String, Object>> logs(
          @RequestParam(defaultValue = "1") int page,
          @RequestParam(defaultValue = "20") int size,
          @RequestParam(required = false) String operator,
          @RequestParam(required = false) String operationType,
          @RequestParam(required = false) String startDate,
          @RequestParam(required = false) String endDate) {
    // 分页查询 mo_audit_log 表，按 createTime 降序排列
    // 字段名映射为前端 OperationLog.vue 期望的格式：operator / operationType / content / ipAddress / operationTime
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuditLogEntity> pageResult =
      auditLogMapper.selectPage(
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
        new LambdaQueryWrapper<AuditLogEntity>()
          .like(org.springframework.util.StringUtils.hasText(operator), AuditLogEntity::getOperatorName, operator)
          .eq(org.springframework.util.StringUtils.hasText(operationType), AuditLogEntity::getAction, operationType)
          .ge(org.springframework.util.StringUtils.hasText(startDate), AuditLogEntity::getCreateTime, startDate)
          .le(org.springframework.util.StringUtils.hasText(endDate), AuditLogEntity::getCreateTime, endDate + " 23:59:59")
          .orderByDesc(AuditLogEntity::getCreateTime));

    List<Map<String, Object>> list = new ArrayList<>();
    for (AuditLogEntity log : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", log.getId());
      item.put("operator", log.getOperatorName());
      item.put("operationType", log.getAction());
      item.put("content", log.getDetail());
      item.put("ipAddress", log.getIp());
      item.put("operationTime", log.getCreateTime() != null ? log.getCreateTime().toString() : null);
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
