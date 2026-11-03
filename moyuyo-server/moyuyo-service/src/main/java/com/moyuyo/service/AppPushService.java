package com.moyuyo.service;

/**
 * App Push 推送服务（手机通知栏推送）。
 * <p>
 * 平台分流：
 * - Android 走 FCM（{@link com.moyuyo.service.impl.FcmAppPushServiceImpl}）
 * - iOS 走 APNs HTTP/2（{@link com.moyuyo.service.impl.ApnsAppPushServiceImpl}）
 * <p>
 * 调用方（推送管理）根据 mo_device.platform 选择 supports() 为 true 的实现。
 */
public interface AppPushService {

  /**
   * 是否支持该设备平台
   *
   * @param platform mo_device.platform（IOS / ANDROID / WEB ...）
   */
  boolean supports(String platform);

  /**
   * 向单台设备发送推送。
   *
   * @param pushToken 设备上报的推送 token（mo_device.push_token）
   * @param title     通知标题
   * @param body      通知正文
   * @throws IllegalStateException 未配置 / 请求失败时抛出，由调用方计入失败数
   */
  void send(String pushToken, String title, String body);
}
