package com.moyuyo.common.dto.prime;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prime 订阅状态响应（用于前端"已开通"视图）。
 */
@Data
public class PrimeStatusVO {

  /** true = 当前有有效订阅 */
  private Boolean active;

  /** 订阅方案：MONTHLY / ANNUAL；未订阅时为 null */
  private String plan;

  /** 中文方案名称：月付 / 年付 */
  private String planName;

  /** 订阅状态：ACTIVE / CANCELLED / EXPIRED */
  private String status;

  /** 到期时间（格式 yyyy-MM-dd HH:mm:ss） */
  private String expireAt;

  /** 是否自动续费 */
  private Boolean autoRenew;

  /** 订阅创建时间 */
  private String createTime;

  /** 本月累计节省金额（元，硬编码为 0，前端展示"待统计"） */
  private String savedThisMonth;
}