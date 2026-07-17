package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.AuditLogEntity;
import com.moyuyo.dao.admin.mapper.AuditLogMapper;
import com.moyuyo.service.admin.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台审计日志服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

  private final AuditLogMapper auditLogMapper;

  @Override
  public Page<Map<String, Object>> listAll(String action, String module, int page, int size) {
    LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
    if (action != null && !action.isEmpty()) {
      wrapper.eq(AuditLogEntity::getAction, action);
    }
    if (module != null && !module.isEmpty()) {
      wrapper.eq(AuditLogEntity::getModule, module);
    }
    wrapper.orderByDesc(AuditLogEntity::getCreateTime);

    Page<AuditLogEntity> entityPage = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("operatorName", e.getOperatorName());
      item.put("action", e.getAction());
      item.put("module", e.getModule());
      item.put("resourceId", e.getResourceId());
      item.put("detail", e.getDetail());
      item.put("ip", e.getIp());
      item.put("userAgent", e.getUserAgent());
      item.put("result", e.getResult());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public List<Map<String, Object>> stats() {
    // 按action分组统计数量
    return auditLogMapper.selectList(null).stream()
        .collect(Collectors.groupingBy(AuditLogEntity::getAction, Collectors.counting()))
        .entrySet().stream()
        .map(entry -> {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("action", entry.getKey());
          item.put("count", entry.getValue());
          return item;
        })
        .collect(Collectors.toList());
  }
}
