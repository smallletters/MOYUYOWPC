package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.GdprConsentEntity;
import com.moyuyo.dao.admin.entity.GdprRequestEntity;
import com.moyuyo.dao.admin.mapper.GdprConsentMapper;
import com.moyuyo.dao.admin.mapper.GdprRequestMapper;
import com.moyuyo.dao.admin.mapper.GdprPolicyMapper;
import com.moyuyo.dao.admin.mapper.MinorVerificationMapper;
import com.moyuyo.dao.admin.mapper.MinorConsentProofMapper;
import com.moyuyo.dao.admin.mapper.GdprQuickActionMapper;
import com.moyuyo.dao.admin.entity.GdprPolicyEntity;
import com.moyuyo.dao.admin.entity.MinorVerificationEntity;
import com.moyuyo.dao.admin.entity.MinorConsentProofEntity;
import com.moyuyo.dao.admin.entity.GdprQuickActionEntity;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminGdprService;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台 GDPR 合规服务实现
 */
@Service
@RequiredArgsConstructor
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT null 静态分析差异（LambdaQueryWrapper 的 Function<T,R> 方法引用）
@SuppressWarnings("null")
public class AdminGdprServiceImpl implements AdminGdprService {

  private final GdprConsentMapper gdprConsentMapper;
  private final GdprRequestMapper gdprRequestMapper;
    private final GdprPolicyMapper gdprPolicyMapper;
    private final MinorVerificationMapper minorVerificationMapper;
    private final MinorConsentProofMapper minorConsentProofMapper;
    private final GdprQuickActionMapper gdprQuickActionMapper;

  /** 管理端时间展示格式（与前端 formatTime 保持一致：yyyy-MM-dd HH:mm） */
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    result.put("titleEn", policy.getTitleEn());
    result.put("content", policy.getContent());
    result.put("contentEn", policy.getContentEn());
    result.put("effectiveDate", policy.getEffectiveDate());
    result.put("createTime", policy.getCreateTime());
    return result;
  }

  @Override
  public Map<String, Object> getLocalizedPolicy(String lang) {
    // 后台完整政策（含中英双语），再按客户端语言裁剪，避免客户端拿到两套正文
    Map<String, Object> full = getActivePolicy();
    if (full.isEmpty()) {
      return full;
    }
    boolean english = lang != null && lang.toLowerCase().startsWith("en");
    String primaryContent = english ? asText(full.get("contentEn")) : asText(full.get("content"));
    String primaryTitle = english ? asText(full.get("titleEn")) : asText(full.get("title"));
    // 对应语言未配置时回退到另一种语言，避免客户端拿到空正文而只能显示内置文案
    boolean fallback = primaryContent == null || primaryContent.isBlank();
    String content = fallback ? (english ? asText(full.get("content")) : asText(full.get("contentEn"))) : primaryContent;
    String title = fallback ? (english ? asText(full.get("title")) : asText(full.get("titleEn"))) : primaryTitle;
    // 回退时实际返回语言与请求语言相反
    String actualLang = english != fallback ? "en-US" : "zh-CN";

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", full.get("id"));
    result.put("version", full.get("version"));
    result.put("title", title != null && !title.isBlank() ? title : (actualLang.equals("en-US") ? "Privacy Policy" : "隐私政策"));
    result.put("content", content);
    result.put("effectiveDate", full.get("effectiveDate"));
    result.put("lang", actualLang);
    return result;
  }

  /** 把 Map 中的值安全转成字符串（兼容 null / 非字符串） */
  private String asText(Object value) {
    return value == null ? null : value.toString();
  }

  @Override
  public Map<String, Object> createPolicy(Map<String, Object> body) {
    // 新建隐私政策版本，默认置为 ACTIVE 生效
    GdprPolicyEntity policy = new GdprPolicyEntity();
    policy.setVersion((String) body.getOrDefault("version", "1.0.0"));
    policy.setTitle((String) body.getOrDefault("title", "隐私政策"));
    policy.setTitleEn((String) body.getOrDefault("titleEn", "Privacy Policy"));
    policy.setContent((String) body.getOrDefault("content", ""));
    policy.setContentEn((String) body.getOrDefault("contentEn", ""));
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
    result.put("titleEn", policy.getTitleEn());
    result.put("content", policy.getContent());
    result.put("contentEn", policy.getContentEn());
    result.put("status", policy.getStatus());
    result.put("effectiveDate", policy.getEffectiveDate());
    return result;
  }

  @Override
  public Map<String, Object> updatePolicy(Long id, Map<String, Object> body) {
    // 只更新传入了的字段，避免把未提交的版本/标题/生效日期覆盖成空值
    GdprPolicyEntity policy = gdprPolicyMapper.selectById(id);
    if (policy == null) {
      throw new IllegalArgumentException("隐私政策不存在");
    }
    if (body.containsKey("title")) {
      policy.setTitle((String) body.get("title"));
    }
    if (body.containsKey("titleEn")) {
      policy.setTitleEn((String) body.get("titleEn"));
    }
    if (body.containsKey("version")) {
      policy.setVersion((String) body.get("version"));
    }
    if (body.containsKey("content")) {
      policy.setContent((String) body.get("content"));
    }
    if (body.containsKey("contentEn")) {
      policy.setContentEn((String) body.get("contentEn"));
    }
    if (body.containsKey("status")) {
      policy.setStatus((String) body.get("status"));
    }
    Object effectiveDate = body.get("effectiveDate");
    if (effectiveDate != null && !effectiveDate.toString().isEmpty()) {
      policy.setEffectiveDate(LocalDate.parse(effectiveDate.toString()));
    }
    gdprPolicyMapper.updateById(policy);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", policy.getId());
    result.put("version", policy.getVersion());
    result.put("title", policy.getTitle());
    result.put("titleEn", policy.getTitleEn());
    result.put("content", policy.getContent());
    result.put("contentEn", policy.getContentEn());
    result.put("status", policy.getStatus());
    result.put("effectiveDate", policy.getEffectiveDate());
    return result;
  }

  // ========== 未成年人保护 ==========

  @Override
  public Map<String, Object> getMinorStats() {
    // 按声明年龄统计：13 岁以下禁止收集，16 岁以下需监护人同意
    LambdaQueryWrapper<MinorVerificationEntity> under13Wrapper = new LambdaQueryWrapper<MinorVerificationEntity>()
        .isNotNull(MinorVerificationEntity::getAge)
        .gt(MinorVerificationEntity::getAge, 0)
        .lt(MinorVerificationEntity::getAge, 13);
    LambdaQueryWrapper<MinorVerificationEntity> under16Wrapper = new LambdaQueryWrapper<MinorVerificationEntity>()
        .isNotNull(MinorVerificationEntity::getAge)
        .gt(MinorVerificationEntity::getAge, 0)
        .lt(MinorVerificationEntity::getAge, 16);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("under13", minorVerificationMapper.selectCount(under13Wrapper));
    result.put("under16", minorVerificationMapper.selectCount(under16Wrapper));
    return result;
  }

  @Override
  public List<Map<String, Object>> listMinorVerifications(int limit) {
    // 限制单次返回条数，避免全表扫描拖慢管理端首屏
    int safeLimit = Math.min(Math.max(limit, 1), 200);
    LambdaQueryWrapper<MinorVerificationEntity> wrapper = new LambdaQueryWrapper<MinorVerificationEntity>()
        .orderByDesc(MinorVerificationEntity::getVerifiedTime)
        .last("LIMIT " + safeLimit);
    return minorVerificationMapper.selectList(wrapper).stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("userId", e.getUserId());
      item.put("age", e.getAge());
      item.put("verifyStatus", e.getVerifyStatus());
      item.put("guardianConsent", guardianConsentLabel(e.getGuardianConsent()));
      item.put("riskLevel", riskLevelLabel(e.getRiskLevel()));
      item.put("verifyChannel", e.getVerifyChannel());
      item.put("lastCheck", formatDateTime(e.getVerifiedTime()));
      item.put("nextCheckTime", formatDateTime(e.getNextCheckTime()));
      return item;
    }).collect(Collectors.toList());
  }

  @Override
  public void reverifyMinor(Long userId) {
    // 重新发起验证：置为待验证并刷新验证/复检时间，由后续验证流程回写结果
    MinorVerificationEntity entity = minorVerificationMapper.selectOne(
        new LambdaQueryWrapper<MinorVerificationEntity>().eq(MinorVerificationEntity::getUserId, userId));
    if (entity == null) {
      throw new IllegalArgumentException("该用户暂无年龄验证记录");
    }
    LocalDateTime now = LocalDateTime.now();
    entity.setVerifyStatus("PENDING");
    entity.setVerifiedTime(now);
    entity.setNextCheckTime(now.plusDays(7));
    entity.setVerifiedBy("管理后台");
    minorVerificationMapper.updateById(entity);
  }

  @Override
  public List<Map<String, Object>> listConsentProofs(Long userId) {
    LambdaQueryWrapper<MinorConsentProofEntity> wrapper = new LambdaQueryWrapper<MinorConsentProofEntity>()
        .eq(userId != null, MinorConsentProofEntity::getUserId, userId)
        .orderByDesc(MinorConsentProofEntity::getSignedAt);
    return minorConsentProofMapper.selectList(wrapper).stream().map(p -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", p.getId());
      item.put("userId", p.getUserId());
      item.put("guardianName", p.getGuardianName());
      item.put("relationship", p.getRelationship());
      item.put("proofType", p.getProofType());
      item.put("proofUrl", p.getProofUrl());
      item.put("signedAt", formatDateTime(p.getSignedAt()));
      item.put("expireAt", formatDateTime(p.getExpireAt()));
      item.put("status", p.getStatus());
      return item;
    }).collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> getArchiveOverview() {
    Long total = minorConsentProofMapper.selectCount(null);
    Long active = minorConsentProofMapper.selectCount(
        new LambdaQueryWrapper<MinorConsentProofEntity>().eq(MinorConsentProofEntity::getStatus, ACTIVE.name()));
    // 最近 5 份凭证，供后台入口快速预览
    LambdaQueryWrapper<MinorConsentProofEntity> recentWrapper = new LambdaQueryWrapper<MinorConsentProofEntity>()
        .orderByDesc(MinorConsentProofEntity::getSignedAt)
        .last("LIMIT 5");
    List<Map<String, Object>> recent = minorConsentProofMapper.selectList(recentWrapper).stream().map(p -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", p.getId());
      item.put("userId", p.getUserId());
      item.put("guardianName", p.getGuardianName());
      item.put("relationship", p.getRelationship());
      item.put("status", p.getStatus());
      item.put("signedAt", formatDateTime(p.getSignedAt()));
      return item;
    }).collect(Collectors.toList());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", total);
    result.put("active", active);
    result.put("recent", recent);
    return result;
  }

  // ========== 快速操作 ==========

  @Override
  public Map<String, Object> executeQuickAction(Map<String, Object> body) {
    String actionType = asText(body.getOrDefault("actionType", "EXPORT"));
    GdprQuickActionEntity entity = new GdprQuickActionEntity();
    entity.setActionType(actionType);
    entity.setTargetUserId(toLong(body.get("targetUserId")));
    entity.setFormat(asText(body.get("format")));
    entity.setNote(asText(body.get("note")));
    entity.setStatus("PENDING");
    entity.setOperatorId(UserContextHolder.getUserId());
    entity.setOperatorName(UserContextHolder.getOperator());
    LocalDateTime now = LocalDateTime.now();
    // 账号删除进入 30 天宽限期，期间可撤销（与前端提示文案一致）
    if ("DELETE".equals(actionType)) {
      entity.setGracePeriodEnd(now.plusDays(30));
    }
    gdprQuickActionMapper.insert(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("actionType", entity.getActionType());
    result.put("status", entity.getStatus());
    result.put("downloadUrl", entity.getDownloadUrl());
    result.put("gracePeriodEnd", formatDateTime(entity.getGracePeriodEnd()));
    return result;
  }

  private String guardianConsentLabel(String value) {
    if (value == null) {
      return "未取得";
    }
    return switch (value) {
      case "GRANTED" -> "已取得";
      case "PENDING" -> "待确认";
      case "EXEMPTED" -> "免同意";
      default -> "未取得";
    };
  }

  private String riskLevelLabel(String value) {
    if (value == null) {
      return "低";
    }
    return switch (value) {
      case "HIGH" -> "高";
      case "MIDDLE" -> "中";
      default -> "低";
    };
  }

  private String formatDateTime(LocalDateTime time) {
    return time == null ? null : time.format(DATE_TIME_FORMATTER);
  }

  private Long toLong(Object value) {
    if (value == null || value.toString().isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value.toString().trim());
    } catch (NumberFormatException e) {
      // 非法用户ID按未指定处理，避免快速操作因脏参数整体失败
      return null;
    }
  }
}
