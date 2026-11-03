package com.moyuyo.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.dao.admin.mapper.PushRecordMapper;
import com.moyuyo.service.admin.PushManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时推送调度任务。
 * <p>
 * 每 60 秒扫描一次 status=SCHEDULED 且 scheduledTime 已到点的推送记录，
 * 调用 {@link PushManageService#send(Long)} 批量写入站内通知并回写发送状态。
 * <p>
 * 注意：
 * - 单实例部署，无需分布式锁；若多实例需换成 ShedLock 或 Redis 分布式锁
 * - send() 内部通过条件 UPDATE 抢占 SENDING，可防止与手动发送重复执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
// 抑制 JDT 静态分析对 MyBatis-Plus LambdaQueryWrapper 方法引用的 @Nonnull 类型告警
@SuppressWarnings("null")
public class PushScheduleSendTask {

  /** 每分钟 0 秒触发 */
  private static final String CRON = "0 * * * * ?";

  private final PushRecordMapper pushRecordMapper;
  private final PushManageService pushManageService;

  @Scheduled(cron = CRON)
  public void tick() {
    List<PushRecordEntity> records;
    try {
      records = pushRecordMapper.selectList(new LambdaQueryWrapper<PushRecordEntity>()
        .eq(PushRecordEntity::getStatus, "SCHEDULED")
        .le(PushRecordEntity::getScheduledTime, LocalDateTime.now())
        .orderByAsc(PushRecordEntity::getScheduledTime));
    } catch (Exception e) {
      log.error("[push-schedule] 查询待发送推送失败: {}", e.getMessage(), e);
      return;
    }

    for (PushRecordEntity record : records) {
      try {
        int sentCount = pushManageService.send(record.getId());
        log.info("[push-schedule] 定时推送已发送: id={}, 成功={}", record.getId(), sentCount);
      } catch (Exception e) {
        // 单条失败不影响后续任务；记录保持 SCHEDULED 由下一轮重试，
        // 参数类错误（如指定用户为空）会持续失败，需管理员在后台修正
        log.error("[push-schedule] 定时推送发送失败: id={}, reason={}", record.getId(), e.getMessage());
      }
    }
  }
}
