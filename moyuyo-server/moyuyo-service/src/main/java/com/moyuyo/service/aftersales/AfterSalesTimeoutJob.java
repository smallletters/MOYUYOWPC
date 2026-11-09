package com.moyuyo.service.aftersales;

import com.moyuyo.service.AfterSalesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 售后超时自动关闭定时任务
 * <p>
 * 解决两类"进行中"状态被永久占用的问题：
 * 1. PENDING 长期未审核：商家不处理，用户既不能推进也不能重新申请；
 * 2. APPROVED 长期未寄回：用户不寄回，该商品的分次退货被"进行中"申请阻塞。
 * <p>
 * 关闭后状态置为 CANCELLED，并给用户发站内信/推送说明原因，用户可重新提交。
 * 幂等由"状态条件更新"保证：已被审核/取消的记录不会被覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSalesTimeoutJob {

  private final AfterSalesService afterSalesService;

  /** 总开关，dev 环境可关闭 */
  @Value("${moyuyo.after-sales.timeout.enabled:true}")
  private boolean enabled;

  /** 待审核超时天数：默认 7 天 */
  @Value("${moyuyo.after-sales.timeout.pending-days:7}")
  private int pendingDays;

  /** 待寄回超时天数：默认 7 天，与 APP 端"7 天内寄回"提示保持一致 */
  @Value("${moyuyo.after-sales.timeout.return-days:7}")
  private int returnDays;

  /** 每小时执行一次，避开订单类任务的高峰时段 */
  @Scheduled(cron = "${moyuyo.after-sales.timeout.cron:0 15 * * * ?}")
  public void closeTimeoutAfterSales() {
    if (!enabled) {
      return;
    }
    try {
      int closed = afterSalesService.closeTimeoutAfterSales(pendingDays, returnDays);
      if (closed > 0) {
        log.info("[after-sales-timeout] 自动关闭售后单 {} 条", closed);
      }
    } catch (Exception e) {
      // 定时任务异常不能影响后续调度
      log.error("[after-sales-timeout] 定时任务异常", e);
    }
  }
}
