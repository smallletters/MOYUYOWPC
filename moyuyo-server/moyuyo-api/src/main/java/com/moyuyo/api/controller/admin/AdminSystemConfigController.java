package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.AuditLogEntity;
import com.moyuyo.dao.admin.mapper.AuditLogMapper;
import com.moyuyo.service.admin.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 系统配置")
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    private final AuditLogMapper auditLogMapper;

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> getConfig(@RequestParam(defaultValue = "basic") String group) {
        Map<String, Object> config = systemConfigService.getConfig(group);
        // 将 Map 转换为前端期望的 List 格式
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("value", entry.getValue());
            item.put("type", "text");
            item.put("label", entry.getKey());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "保存配置")
    @PutMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestParam(defaultValue = "basic") String group,
                                                   @RequestBody List<Map<String, Object>> configs) {
        // 将前端传入的 List 转换为 Map
        Map<String, Object> configMap = new LinkedHashMap<>();
        for (Map<String, Object> item : configs) {
            configMap.put((String) item.get("key"), item.get("value"));
        }
        systemConfigService.saveConfig(group, configMap);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", configs.size());
        result.put("message", "配置保存成功");
        return Result.success(result);
    }

    @Operation(summary = "操作/运营日志")
    @GetMapping("/logs")
    public Result<List<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "operation") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        // 从 mo_audit_log 表查询真实日志数据，按 createTime 降序排列
        List<AuditLogEntity> logList = auditLogMapper.selectList(
          new LambdaQueryWrapper<AuditLogEntity>()
            .orderByDesc(AuditLogEntity::getCreateTime));
        for (AuditLogEntity log : logList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("operator", log.getOperatorName());
            item.put("action", log.getAction());
            item.put("detail", log.getDetail());
            item.put("ip", log.getIp());
            item.put("createTime", log.getCreateTime());
            list.add(item);
        }
        return Result.success(list);
    }

    private Map<String, Object> createConfigItem(String key, String value, String type, String label) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("value", value);
        item.put("type", type);
        item.put("label", label);
        return item;
    }
}
