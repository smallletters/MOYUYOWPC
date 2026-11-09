package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.UserDeviceEntity;
import com.moyuyo.dao.mapper.UserDeviceMapper;
import com.moyuyo.service.AppPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App 推送异步执行器
 * <p>
 * 独立 Bean 的原因：{@link Async} 基于 Spring 代理，同类内部调用不生效。
 * 推送是网络 I/O（FCM/APNs 单次可达数百毫秒），批量发货、超时关闭等场景
 * 若同步执行会显著拉长业务请求耗时，因此统一放到 moyuyoTaskExecutor 异步执行。
 * <p>
 * 线程池拒绝策略为 CallerRuns（见 AsyncConfig），过载时回压到调用线程，不会丢任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushDispatcher {

  /** APP 原生推送插件未真实接入时返回的占位 token，过滤避免向无效 token 推送 */
  private static final String PLACEHOLDER_TOKEN_KEYWORD = "placeholder";

  private final UserDeviceMapper userDeviceMapper;
  /** 已启用的 App Push 实现（Android→FCM，iOS→APNs）；均未配置时为空列表 */
  private final List<AppPushService> appPushServices;

  /**
   * 异步向用户已上报的 pushToken 投递通知栏推送
   * <p>
   * 同一 token 可能出现在多条设备记录，按 token 去重；占位 token 直接过滤；
   * 任何异常只记日志，不影响站内信。
   */
  @Async("moyuyoTaskExecutor")
  public void dispatch(Long userId, String title, String content) {
    if (userId == null || appPushServices == null || appPushServices.isEmpty()) {
      return;
    }
    try {
      List<UserDeviceEntity> devices = userDeviceMapper.selectList(
          new LambdaQueryWrapper<UserDeviceEntity>()
              .eq(UserDeviceEntity::getUserId, userId)
              .select(UserDeviceEntity::getPushToken, UserDeviceEntity::getPlatform));
      Map<String, String> tokenPlatforms = new LinkedHashMap<>();
      for (UserDeviceEntity device : devices) {
        String token = device.getPushToken() == null ? "" : device.getPushToken().trim()
            .replace("<", "").replace(">", "").replace(" ", "");
        if (token.isEmpty() || token.contains(PLACEHOLDER_TOKEN_KEYWORD)) {
          continue;
        }
        tokenPlatforms.putIfAbsent(token, device.getPlatform());
      }
      for (Map.Entry<String, String> entry : tokenPlatforms.entrySet()) {
        AppPushService sender = appPushServices.stream()
            .filter(service -> service.supports(entry.getValue()))
            .findFirst()
            .orElse(null);
        if (sender == null) {
          continue;
        }
        try {
          sender.send(entry.getKey(), title, content);
        } catch (Exception e) {
          log.warn("[push] 推送发送失败: userId={}, platform={}, reason={}",
              userId, entry.getValue(), e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("[push] 推送任务异常: userId={}, reason={}", userId, e.getMessage());
    }
  }
}
