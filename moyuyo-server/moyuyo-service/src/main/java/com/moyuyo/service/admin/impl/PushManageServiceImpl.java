package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.dao.admin.mapper.PushRecordMapper;
import com.moyuyo.dao.entity.NotificationEntity;
import com.moyuyo.dao.entity.UserDeviceEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.NotificationMapper;
import com.moyuyo.dao.mapper.UserDeviceMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AppPushService;
import com.moyuyo.service.EmailService;
import com.moyuyo.service.SmsService;
import com.moyuyo.service.admin.PushManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推送管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 抑制 JDT 静态分析对 MyBatis-Plus LambdaQueryWrapper 方法引用的 @Nonnull 类型告警
// （MyBatis-Plus 3.x 的 Function<T,R> 泛型推断与 JDT null 分析存在已知差异，运行时安全）
@SuppressWarnings("null")
public class PushManageServiceImpl implements PushManageService {

  /** 站内通知类型：与 APP 通知中心「系统」Tab 的 key 保持一致 */
  private static final String NOTICE_TYPE_SYSTEM = "system";

  /** 用户状态：1=ACTIVE（与 AdminUserProfileServiceImpl.stats 统计口径一致） */
  private static final int USER_STATUS_ACTIVE = 1;

  /** 发送进度日志打印间隔（条） */
  private static final int PROGRESS_LOG_INTERVAL = 500;

  /** APP 原生推送插件未真实接入时返回的占位 token，过滤避免向无效 token 推送 */
  private static final String PLACEHOLDER_TOKEN_KEYWORD = "placeholder";

  private final PushRecordMapper pushRecordMapper;
  private final UserMapper userMapper;
  private final UserDeviceMapper userDeviceMapper;
  private final NotificationMapper notificationMapper;
  private final SmsService smsService;
  private final EmailService emailService;
  /** 所有已启用的 App Push 实现（Android→FCM，iOS→APNs）；均未配置时为空列表 */
  private final List<AppPushService> appPushServices;

  @Override
  public List<PushRecordEntity> listRecords(String status, String type) {
    LambdaQueryWrapper<PushRecordEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(PushRecordEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(PushRecordEntity::getType, type);
    }
    wrapper.orderByDesc(PushRecordEntity::getCreateTime);
    return pushRecordMapper.selectList(wrapper);
  }

  @Override
  public void create(PushRecordEntity entity) {
    pushRecordMapper.insert(entity);
  }

  @Override
  public void update(PushRecordEntity entity) {
    pushRecordMapper.updateById(entity);
  }

  /**
   * 发送推送：按渠道分发到站内通知 / SMS / EMAIL，APP 渠道额外尽力投递 FCM 手机推送。
   * <p>
   * 渠道由推送记录的 type 字段决定（与前端 channel 映射一致）：
   * APP（含 all/ios/android 等历史值）→ 写 mo_notification + FCM；
   * SMS → 阿里云短信通知模板；EMAIL → SMTP 邮件。
   * <p>
   * 并发控制：通过条件 UPDATE 把状态原子切换为 SENDING，只有抢占成功的请求才执行群发，
   * 避免管理员重复点击或定时任务与手动发送同时触发造成重复发送。
   */
  @Override
  public int send(Long id) {
    PushRecordEntity record = pushRecordMapper.selectById(id);
    if (record == null) {
      throw new IllegalArgumentException("推送记录不存在");
    }

    String currentStatus = normalizeStatus(record.getStatus());
    if ("SENT".equals(currentStatus)) {
      // 幂等：已发送过则直接返回原成功数，不重复发送
      log.info("[push] 推送已发送，跳过重复发送: id={}", id);
      return record.getSuccessCount() == null ? 0 : record.getSuccessCount();
    }
    if ("CANCELLED".equals(currentStatus) || "CANCELED".equals(currentStatus)) {
      throw new IllegalArgumentException("已取消的推送不能发送");
    }

    // 先解析目标人群：参数非法时直接返回 400，避免状态被误改为 SENDING
    TargetUsers target = resolveTargetUsers(record);

    // 原子抢占：仅状态不是 SENT/SENDING/CANCELLED 时才允许切换为 SENDING
    int claimed = pushRecordMapper.update(null, new LambdaUpdateWrapper<PushRecordEntity>()
      .eq(PushRecordEntity::getId, id)
      .notIn(PushRecordEntity::getStatus, "SENT", "SENDING", "CANCELLED", "CANCELED")
      .set(PushRecordEntity::getStatus, "SENDING"));
    if (claimed == 0) {
      PushRecordEntity latest = pushRecordMapper.selectById(id);
      // 显式判空，避免抢占失败后记录被并发删除导致 NPE
      if (latest == null) {
        throw new IllegalArgumentException("推送记录不存在");
      }
      if ("SENT".equals(normalizeStatus(latest.getStatus()))) {
        return latest.getSuccessCount() == null ? 0 : latest.getSuccessCount();
      }
      throw new IllegalArgumentException("推送正在发送中，请勿重复操作");
    }

    String channel = normalizeChannel(record.getType());
    SendResult result = switch (channel) {
      case "SMS" -> sendSms(record, target.users());
      case "EMAIL" -> sendEmail(record, target.users());
      default -> sendInApp(record, target.users());
    };

    int successCount = result.success();
    // 指定用户中不存在（已注销/删除）的 ID 也计入失败数，便于管理员核对
    int failCount = result.fail() + target.missingCount();

    PushRecordEntity finish = new PushRecordEntity();
    finish.setId(id);
    finish.setSuccessCount(successCount);
    finish.setFailCount(failCount);
    finish.setSentTime(LocalDateTime.now());
    // 至少成功一条即视为已发送，全部失败则标记 FAILED 方便列表区分
    finish.setStatus(successCount > 0 ? "SENT" : "FAILED");
    pushRecordMapper.updateById(finish);

    log.info("[push] 发送完成: id={}, 渠道={}, 目标用户={}, 成功={}, 失败={}",
      id, channel, target.users().size() + target.missingCount(), successCount, failCount);
    return successCount;
  }

  /**
   * APP 渠道：写入站内通知（保证 APP 内可见），再尽力投递 FCM 手机推送
   */
  private SendResult sendInApp(PushRecordEntity record, List<UserEntity> users) {
    int success = 0;
    int fail = 0;
    for (UserEntity user : users) {
      try {
        notificationMapper.insert(buildNotification(record, user.getId()));
        success++;
      } catch (Exception e) {
        fail++;
        log.warn("[push] 站内通知写入失败: pushId={}, userId={}, reason={}",
          record.getId(), user.getId(), e.getMessage());
      }
      logProgress(record.getId(), success + fail);
    }
    sendAppPush(record, users);
    return new SendResult(success, fail);
  }

  /**
   * SMS 渠道：逐用户调用阿里云通知模板；没有手机号的用户计入失败
   */
  private SendResult sendSms(PushRecordEntity record, List<UserEntity> users) {
    int success = 0;
    int fail = 0;
    for (UserEntity user : users) {
      String phone = user.getPhone();
      if (phone == null || phone.isBlank()) {
        fail++;
        continue;
      }
      try {
        smsService.sendNotice(phone, record.getTitle(), record.getContent());
        success++;
      } catch (Exception e) {
        fail++;
        log.warn("[push] 短信发送失败: pushId={}, userId={}, reason={}",
          record.getId(), user.getId(), e.getMessage());
      }
      logProgress(record.getId(), success + fail);
    }
    return new SendResult(success, fail);
  }

  /**
   * EMAIL 渠道：逐用户 SMTP 发送；没有邮箱的用户计入失败
   */
  private SendResult sendEmail(PushRecordEntity record, List<UserEntity> users) {
    int success = 0;
    int fail = 0;
    for (UserEntity user : users) {
      String email = user.getEmail();
      if (email == null || email.isBlank()) {
        fail++;
        continue;
      }
      try {
        emailService.sendNotification(email, record.getTitle(), record.getContent());
        success++;
      } catch (Exception e) {
        fail++;
        log.warn("[push] 邮件发送失败: pushId={}, userId={}, reason={}",
          record.getId(), user.getId(), e.getMessage());
      }
      logProgress(record.getId(), success + fail);
    }
    return new SendResult(success, fail);
  }

  /**
   * APP Push：按设备平台分流（Android→FCM，iOS→APNs），向目标用户已上报的 pushToken 投递。
   * <p>
   * 尽力而为：通道未启用/单设备失败只记录日志，不影响站内通知的成功计数。
   */
  private void sendAppPush(PushRecordEntity record, List<UserEntity> users) {
    if (appPushServices.isEmpty()) {
      log.info("[push] FCM/APNs 均未启用，跳过 App Push: id={}", record.getId());
      return;
    }
    List<Long> userIds = users.stream()
      .map(UserEntity::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    if (userIds.isEmpty()) {
      return;
    }
    List<UserDeviceEntity> devices = userDeviceMapper.selectList(new LambdaQueryWrapper<UserDeviceEntity>()
      .in(UserDeviceEntity::getUserId, userIds)
      .select(UserDeviceEntity::getPushToken, UserDeviceEntity::getPlatform));
    // token → platform：同一 token 可能出现在多条设备记录，按 token 去重
    Map<String, String> tokenPlatforms = new LinkedHashMap<>();
    for (UserDeviceEntity device : devices) {
      String token = device.getPushToken() == null ? "" : device.getPushToken().trim()
        .replace("<", "").replace(">", "").replace(" ", "");
      if (token.isEmpty() || token.contains(PLACEHOLDER_TOKEN_KEYWORD)) {
        continue;
      }
      tokenPlatforms.putIfAbsent(token, device.getPlatform());
    }
    if (tokenPlatforms.isEmpty()) {
      log.info("[push] 没有可用的 pushToken，跳过 App Push: id={}", record.getId());
      return;
    }

    int success = 0;
    int fail = 0;
    int skipped = 0;
    for (Map.Entry<String, String> entry : tokenPlatforms.entrySet()) {
      AppPushService sender = appPushServices.stream()
        .filter(service -> service.supports(entry.getValue()))
        .findFirst()
        .orElse(null);
      if (sender == null) {
        // 该平台对应通道未配置（如 APNs 未启用时的 iOS 设备）
        skipped++;
        continue;
      }
      try {
        sender.send(entry.getKey(), record.getTitle(), record.getContent());
        success++;
      } catch (Exception e) {
        fail++;
        log.warn("[push] App Push 发送失败: pushId={}, platform={}, reason={}",
          record.getId(), entry.getValue(), e.getMessage());
      }
    }
    log.info("[push] App Push 完成: id={}, 设备={}, 成功={}, 失败={}, 通道未配置跳过={}",
      record.getId(), tokenPlatforms.size(), success, fail, skipped);
  }

  @Override
  public void cancel(Long id) {
    PushRecordEntity entity = new PushRecordEntity();
    entity.setId(id);
    entity.setStatus("CANCELLED");
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    pushRecordMapper.deleteById(id);
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();

    LocalDate today = LocalDate.now();
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

    // 今日推送数
    LambdaQueryWrapper<PushRecordEntity> todayWrapper = new LambdaQueryWrapper<>();
    todayWrapper.ge(PushRecordEntity::getCreateTime, todayStart);
    long todayPushCount = pushRecordMapper.selectCount(todayWrapper);

    // 本月推送数
    LambdaQueryWrapper<PushRecordEntity> monthWrapper = new LambdaQueryWrapper<>();
    monthWrapper.ge(PushRecordEntity::getCreateTime, monthStart);
    long monthPushCount = pushRecordMapper.selectCount(monthWrapper);

    // 从已发送的推送记录中统计成功/失败数
    LambdaQueryWrapper<PushRecordEntity> sentWrapper = new LambdaQueryWrapper<>();
    sentWrapper.eq(PushRecordEntity::getStatus, "SENT");
    List<PushRecordEntity> sentRecords = pushRecordMapper.selectList(sentWrapper);

    long totalSuccess = 0;
    long totalFail = 0;
    for (PushRecordEntity record : sentRecords) {
      totalSuccess += record.getSuccessCount() != null ? record.getSuccessCount() : 0;
      totalFail += record.getFailCount() != null ? record.getFailCount() : 0;
    }

    // 计算成功率
    BigDecimal successRate = BigDecimal.ZERO;
    long total = totalSuccess + totalFail;
    if (total > 0) {
      successRate = BigDecimal.valueOf(totalSuccess)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    stats.put("todayPushCount", todayPushCount);
    stats.put("monthPushCount", monthPushCount);
    stats.put("successRate", successRate);
    stats.put("subscribedUsers", BigDecimal.valueOf(totalSuccess));

    return stats;
  }

  @Override
  public List<PushRecordEntity> listScheduledRecords() {
    LambdaQueryWrapper<PushRecordEntity> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(PushRecordEntity::getStatus, "SCHEDULED")
      .orderByAsc(PushRecordEntity::getScheduledTime);
    return pushRecordMapper.selectList(wrapper);
  }

  @Override
  public void saveSchedule(PushRecordEntity record) {
    record.setStatus("SCHEDULED");
    pushRecordMapper.insert(record);
  }

  /**
   * 解析目标人群
   * <p>
   * targetType 约定：ALL（默认，全部活跃用户）/ USER（指定用户，读 targetIds）/ TAG（暂不支持）。
   * targetIds 兼容 JSON 数组（[1001,1002]）与逗号分隔串（1001,1002）。
   */
  private TargetUsers resolveTargetUsers(PushRecordEntity record) {
    String targetType = record.getTargetType() == null
      ? "ALL" : record.getTargetType().trim().toUpperCase();
    switch (targetType) {
      case "USER":
      case "USERS":
        return resolveSpecifiedUsers(record.getTargetIds());
      case "TAG":
      case "TAGS":
        // 用户标签体系尚未落地，明确拒绝而不是静默群发给全部用户
        throw new IllegalArgumentException("暂不支持按标签推送，请选择全部用户或指定用户");
      default:
        return resolveAllActiveUsers();
    }
  }

  /**
   * 全部活跃用户（仅取发送所需字段，避免拉取密码等敏感列）
   */
  private TargetUsers resolveAllActiveUsers() {
    List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
      .eq(UserEntity::getStatus, USER_STATUS_ACTIVE)
      .select(UserEntity::getId, UserEntity::getEmail, UserEntity::getPhone));
    if (users.isEmpty()) {
      log.warn("[push] 没有可推送的活跃用户");
    }
    return new TargetUsers(users, 0);
  }

  /**
   * 指定用户：过滤出真实存在的用户，缺失的 ID 计入失败数
   */
  private TargetUsers resolveSpecifiedUsers(String rawTargetIds) {
    List<Long> targetIds = parseTargetIds(rawTargetIds);
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException("指定用户推送必须提供有效的用户ID");
    }
    List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
      .in(UserEntity::getId, targetIds)
      .select(UserEntity::getId, UserEntity::getEmail, UserEntity::getPhone));
    return new TargetUsers(users, targetIds.size() - users.size());
  }

  /**
   * 解析目标用户 ID：兼容 JSON 数组与逗号分隔串，非法片段仅告警跳过
   */
  private List<Long> parseTargetIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String cleaned = raw.replaceAll("[\\[\\]\"'\\s]", "");
    Set<Long> ids = new LinkedHashSet<>();
    for (String part : cleaned.split(",")) {
      if (part.isEmpty()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(part));
      } catch (NumberFormatException e) {
        log.warn("[push] 忽略非法用户ID: {}", part);
      }
    }
    return new ArrayList<>(ids);
  }

  /**
   * 构造站内通知实体
   */
  private NotificationEntity buildNotification(PushRecordEntity record, Long userId) {
    NotificationEntity entity = new NotificationEntity();
    entity.setUserId(userId);
    entity.setType(NOTICE_TYPE_SYSTEM);
    entity.setTitle(record.getTitle());
    entity.setContent(record.getContent());
    // relatedId 记录推送来源，便于排查
    entity.setRelatedId(record.getId());
    entity.setRead(0);
    return entity;
  }

  /**
   * 渠道归一化：SMS / EMAIL / APP（历史值 all、ios、android、NOTICE 等统一按 APP 处理）
   */
  private String normalizeChannel(String raw) {
    String value = raw == null ? "" : raw.trim().toUpperCase();
    if (value.contains("SMS")) {
      return "SMS";
    }
    if (value.contains("EMAIL") || value.contains("MAIL")) {
      return "EMAIL";
    }
    return "APP";
  }

  private void logProgress(Long id, int processed) {
    if (processed % PROGRESS_LOG_INTERVAL == 0) {
      log.info("[push] 发送进度: id={}, 已处理={}", id, processed);
    }
  }

  private String normalizeStatus(String status) {
    return status == null ? "" : status.trim().toUpperCase();
  }

  /**
   * 目标人群解析结果
   *
   * @param users        实际存在的目标用户
   * @param missingCount 指定用户中不存在的 ID 数量
   */
  private record TargetUsers(List<UserEntity> users, int missingCount) {
  }

  /**
   * 单渠道发送结果
   */
  private record SendResult(int success, int fail) {
  }
}
