package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 分销员排行条目
 */
@Data
public class DistributionTopItem {

  /** 用户 ID */
  private Long userId;

  /** 用户昵称（若无昵称则用 userId 字符串兜底） */
  private String name;

  /** 推广单数 */
  private int orders;

  /** 推广 GMV */
  private BigDecimal gmv;

  /** 佣金 */
  private BigDecimal commission;
}
