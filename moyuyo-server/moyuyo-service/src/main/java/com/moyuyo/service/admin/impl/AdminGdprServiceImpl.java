package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.GdprConsentEntity;
import com.moyuyo.dao.admin.entity.GdprRequestEntity;
import com.moyuyo.dao.admin.mapper.GdprConsentMapper;
import com.moyuyo.dao.admin.mapper.GdprRequestMapper;
import com.moyuyo.dao.admin.mapper.GdprPolicyMapper;
import com.moyuyo.dao.admin.entity.GdprPolicyEntity;
import com.moyuyo.service.admin.AdminGdprService;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台 GDPR 合规服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminGdprServiceImpl implements AdminGdprService {

  private final GdprConsentMapper gdprConsentMapper;
  private final GdprRequestMapper gdprRequestMapper;
    private final GdprPolicyMapper gdprPolicyMapper;

  @Override
  public Page<Map<String, Object>> listConsents(Long userId, int page, int size) {
    LambdaQueryWrapper<GdprConsentEntity> wrapper = new LambdaQueryWrapper<>();
    if (userId != null) {
      wrapper.eq(GdprConsentEntity::getUserId, userId);
    }
    wrapper.orderByDesc(GdprConsentEntity::getCreateTime);

    Page<GdprConsentEntity> entityPage = gdprConsentMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("userId", e.getUserId());
      item.put("consentType", e.getConsentType());
      item.put("granted", e.getGranted());
      item.put("ip", e.getIp());
      item.put("userAgent", e.getUserAgent());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public Page<Map<String, Object>> listRequests(String status, int page, int size) {
    LambdaQueryWrapper<GdprRequestEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(GdprRequestEntity::getStatus, status);
    }
    wrapper.orderByDesc(GdprRequestEntity::getCreateTime);

    Page<GdprRequestEntity> entityPage = gdprRequestMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("userId", e.getUserId());
      item.put("requestType", e.getRequestType());
      item.put("status", e.getStatus());
      item.put("detailJson", e.getDetailJson());
      item.put("processedBy", e.getProcessedBy());
      item.put("processedTime", e.getProcessedTime());
      item.put("responseNote", e.getResponseNote());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public void processRequest(Long id, String result, String note) {
    GdprRequestEntity entity = gdprRequestMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(result);
      entity.setResponseNote(note);
      entity.setProcessedTime(LocalDateTime.now());
      gdprRequestMapper.updateById(entity);
    }
  }

  @Override
  public Map<String, Object> getActivePolicy() {
    // 查询最新生效的隐私政策
    LambdaQueryWrapper<GdprPolicyEntity> wrapper = new LambdaQueryWrapper<GdprPolicyEntity>()
        .eq(GdprPolicyEntity::getStatus, ACTIVE.name())
        .orderByDesc(GdprPolicyEntity::getCreateTime)
        .last("LIMIT 1");
    GdprPolicyEntity policy = gdprPolicyMapper.selectOne(wrapper);
    if (policy == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", policy.getId());
    result.put("version", policy.getVersion());
    result.put("title", policy.getTitle());
    result.put("content", policy.getContent());
    result.put("effectiveDate", policy.getEffectiveDate());
    result.put("createTime", policy.getCreateTime());
    return result;
  }

  @Override
  public Map<String, Object> createPolicy(Map<String, Object> body) {
    // 新建隐私政策版本，默认置为 ACTIVE 生效
    GdprPolicyEntity policy = new GdprPolicyEntity();
    policy.setVersion((String) body.getOrDefault("version", "1.0.0"));
    policy.setTitle((String) body.getOrDefault("title", "隐私政策"));
    policy.setContent((String) body.getOrDefault("content", ""));
    policy.setStatus((String) body.getOrDefault("status", "ACTIVE"));
    Object effectiveDate = body.get("effectiveDate");
    if (effectiveDate != null && !effectiveDate.toString().isEmpty()) {
      policy.setEffectiveDate(LocalDate.parse(effectiveDate.toString()));
    } else {
      policy.setEffectiveDate(LocalDate.now());
    }
    gdprPolicyMapper.insert(policy);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", policy.getId());
    result.put("version", policy.getVersion());
    result.put("title", policy.getTitle());
    result.put("content", policy.getContent());
    result.put("status", policy.getStatus());
    result.put("effectiveDate", policy.getEffectiveDate());
    return result;
  }
}
