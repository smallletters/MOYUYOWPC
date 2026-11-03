package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PushManageServiceImpl 发送链路单元测试（纯 Mock，不连数据库）。
 * <p>
 * 覆盖：
 * 1) 全部用户推送：写入站内通知 + 回写 SENT/成功数
 * 2) 指定用户推送：不存在的用户 ID 计入失败数
 * 3) 已发送记录幂等跳过，不重复写通知
 * 4) 已取消记录拒绝发送
 * 5) 指定用户但未提供用户 ID 时拒绝，且不会抢占状态
 */
@ExtendWith(MockitoExtension.class)
class PushManageServiceImplTest {

  @Mock
  private PushRecordMapper pushRecordMapper;

  @Mock
  private UserMapper userMapper;

  @Mock
  private UserDeviceMapper userDeviceMapper;

  @Mock
  private NotificationMapper notificationMapper;

  @Mock
  private SmsService smsService;

  @Mock
  private EmailService emailService;

  @Mock
  private AppPushService fcmSender;

  @Mock
  private AppPushService apnsSender;

  @Captor
  private ArgumentCaptor<NotificationEntity> notificationCaptor;

  @Captor
  private ArgumentCaptor<PushRecordEntity> recordCaptor;

  private PushManageServiceImpl pushManageService;

  /** 初始化 MyBatis-Plus Lambda 元数据，避免纯 Mockito 单测触发 "lambda cache" 异常 */
  @BeforeAll
  static void initMybatisTableInfo() {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    TableInfoHelper.initTableInfo(assistant, PushRecordEntity.class);
    TableInfoHelper.initTableInfo(assistant, UserDeviceEntity.class);
  }

  @BeforeEach
  void setUp() {
    pushManageService = new PushManageServiceImpl(
        pushRecordMapper, userMapper, userDeviceMapper, notificationMapper,
        smsService, emailService, List.of());
  }

  @Test
  @DisplayName("全部用户推送：写入站内通知并回写 SENT + 成功数")
  void sendAllUsersShouldWriteNotifications() {
    PushRecordEntity record = buildRecord(1L, "PENDING", null, null);
    when(pushRecordMapper.selectById(1L)).thenReturn(record);
    when(userMapper.selectList(any()))
        .thenReturn(List.of(buildUser(1001L), buildUser(1002L)));
    doReturn(1).when(pushRecordMapper).update(isNull(), any());

    int sentCount = pushManageService.send(1L);

    assertEquals(2, sentCount);
    verify(notificationMapper, times(2)).insert(notificationCaptor.capture());
    List<NotificationEntity> notifications = notificationCaptor.getAllValues();
    // 站内通知类型与 APP 通知中心 system tab 对齐
    assertEquals("system", notifications.get(0).getType());
    assertEquals("系统公告标题", notifications.get(0).getTitle());
    assertEquals(1L, notifications.get(0).getRelatedId());
    assertEquals(0, notifications.get(0).getRead());

    verify(pushRecordMapper).updateById(recordCaptor.capture());
    PushRecordEntity finish = recordCaptor.getValue();
    assertEquals("SENT", finish.getStatus());
    assertEquals(2, finish.getSuccessCount());
    assertEquals(0, finish.getFailCount());
  }

  @Test
  @DisplayName("指定用户推送：不存在的用户 ID 计入失败数")
  void sendSpecifiedUsersShouldCountMissingAsFail() {
    PushRecordEntity record = buildRecord(2L, "PENDING", "USER", "1001,1002");
    when(pushRecordMapper.selectById(2L)).thenReturn(record);
    // 只查到 1001，1002 不存在
    when(userMapper.selectList(any())).thenReturn(List.of(buildUser(1001L)));
    doReturn(1).when(pushRecordMapper).update(isNull(), any());

    int sentCount = pushManageService.send(2L);

    assertEquals(1, sentCount);
    verify(notificationMapper, times(1)).insert(any(NotificationEntity.class));
    verify(pushRecordMapper).updateById(recordCaptor.capture());
    assertEquals(1, recordCaptor.getValue().getSuccessCount());
    assertEquals(1, recordCaptor.getValue().getFailCount());
    assertEquals("SENT", recordCaptor.getValue().getStatus());
  }

  @Test
  @DisplayName("已发送记录幂等跳过，不重复写通知")
  void sendSentRecordShouldBeIdempotent() {
    PushRecordEntity record = buildRecord(3L, "SENT", null, null);
    record.setSuccessCount(10);
    when(pushRecordMapper.selectById(3L)).thenReturn(record);

    int sentCount = pushManageService.send(3L);

    assertEquals(10, sentCount);
    verify(userMapper, never()).selectList(any());
    verify(notificationMapper, never()).insert(any(NotificationEntity.class));
    verify(pushRecordMapper, never()).updateById(any(PushRecordEntity.class));
  }

  @Test
  @DisplayName("已取消记录拒绝发送")
  void sendCancelledRecordShouldThrow() {
    PushRecordEntity record = buildRecord(4L, "CANCELLED", null, null);
    when(pushRecordMapper.selectById(4L)).thenReturn(record);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> pushManageService.send(4L));
    assertEquals("已取消的推送不能发送", error.getMessage());
    verify(notificationMapper, never()).insert(any(NotificationEntity.class));
  }

  @Test
  @DisplayName("指定用户但未提供用户 ID：拒绝发送且不抢占状态")
  void sendUserTargetWithoutIdsShouldThrow() {
    PushRecordEntity record = buildRecord(5L, "PENDING", "USER", null);
    when(pushRecordMapper.selectById(5L)).thenReturn(record);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> pushManageService.send(5L));
    assertEquals("指定用户推送必须提供有效的用户ID", error.getMessage());
    verify(pushRecordMapper, never()).update(isNull(), any());
    verify(notificationMapper, never()).insert(any(NotificationEntity.class));
  }

  @Test
  @DisplayName("SMS 渠道：逐用户真实发送，无手机号计入失败")
  void sendSmsShouldCallSmsService() {
    PushRecordEntity record = buildRecord(6L, "PENDING", null, null);
    record.setType("SMS");
    when(pushRecordMapper.selectById(6L)).thenReturn(record);
    when(userMapper.selectList(any())).thenReturn(List.of(
        buildUser(2001L, "user1@example.com", "+8613800000001"),
        buildUser(2002L, "user2@example.com", null)));
    doReturn(1).when(pushRecordMapper).update(isNull(), any());

    int sentCount = pushManageService.send(6L);

    assertEquals(1, sentCount);
    verify(smsService, times(1))
        .sendNotice("+8613800000001", "系统公告标题", "系统公告内容");
    verify(pushRecordMapper).updateById(recordCaptor.capture());
    assertEquals(1, recordCaptor.getValue().getSuccessCount());
    assertEquals(1, recordCaptor.getValue().getFailCount());
    assertEquals("SENT", recordCaptor.getValue().getStatus());
    // SMS 渠道不写站内通知
    verify(notificationMapper, never()).insert(any(NotificationEntity.class));
  }

  @Test
  @DisplayName("EMAIL 渠道：逐用户真实发送，无邮箱计入失败")
  void sendEmailShouldCallEmailService() {
    PushRecordEntity record = buildRecord(7L, "PENDING", null, null);
    record.setType("EMAIL");
    when(pushRecordMapper.selectById(7L)).thenReturn(record);
    when(userMapper.selectList(any())).thenReturn(List.of(
        buildUser(3001L, "user1@example.com", "+8613800000001"),
        buildUser(3002L, null, "+8613800000002")));
    doReturn(1).when(pushRecordMapper).update(isNull(), any());

    int sentCount = pushManageService.send(7L);

    assertEquals(1, sentCount);
    verify(emailService, times(1))
        .sendNotification("user1@example.com", "系统公告标题", "系统公告内容");
    verify(pushRecordMapper).updateById(recordCaptor.capture());
    assertEquals(1, recordCaptor.getValue().getSuccessCount());
    assertEquals(1, recordCaptor.getValue().getFailCount());
    verify(notificationMapper, never()).insert(any(NotificationEntity.class));
  }

  @Test
  @DisplayName("App Push 按设备平台分流：Android→FCM，iOS→APNs")
  void sendAppPushShouldRouteByPlatform() {
    PushRecordEntity record = buildRecord(8L, "PENDING", null, null);
    when(pushRecordMapper.selectById(8L)).thenReturn(record);
    when(userMapper.selectList(any())).thenReturn(List.of(buildUser(4001L)));
    when(userDeviceMapper.selectList(any())).thenReturn(List.of(
        buildDevice("android-token", "ANDROID"),
        buildDevice("ios-token", "IOS")));
    when(fcmSender.supports("ANDROID")).thenReturn(true);
    when(apnsSender.supports("IOS")).thenReturn(true);
    doReturn(1).when(pushRecordMapper).update(isNull(), any());

    PushManageServiceImpl routed = new PushManageServiceImpl(
        pushRecordMapper, userMapper, userDeviceMapper, notificationMapper,
        smsService, emailService, List.of(fcmSender, apnsSender));
    routed.send(8L);

    verify(fcmSender).send("android-token", "系统公告标题", "系统公告内容");
    verify(apnsSender).send("ios-token", "系统公告标题", "系统公告内容");
  }

  private PushRecordEntity buildRecord(Long id, String status, String targetType, String targetIds) {
    PushRecordEntity record = new PushRecordEntity();
    record.setId(id);
    record.setTitle("系统公告标题");
    record.setContent("系统公告内容");
    record.setType("APP");
    record.setStatus(status);
    record.setTargetType(targetType);
    record.setTargetIds(targetIds);
    return record;
  }

  private UserEntity buildUser(Long id, String email, String phone) {
    UserEntity user = new UserEntity();
    user.setId(id);
    user.setStatus(1);
    user.setEmail(email);
    user.setPhone(phone);
    return user;
  }

  private UserEntity buildUser(Long id) {
    return buildUser(id, null, null);
  }

  private UserDeviceEntity buildDevice(String pushToken, String platform) {
    UserDeviceEntity device = new UserDeviceEntity();
    device.setUserId(4001L);
    device.setPushToken(pushToken);
    device.setPlatform(platform);
    return device;
  }
}
