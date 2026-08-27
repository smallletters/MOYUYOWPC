package com.moyuyo.common.dto.prime;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Prime 套餐响应（用于前端套餐卡片展示）。
 */
@Data
public class PrimePlanVO {

  private Long id;

  /** MONTHLY / YEARLY */
  private String code;

  /** 月付 / 年付 */
  private String name;

  private Integer durationMonths;

  private BigDecimal price;

  private BigDecimal originalPrice;

  /** 解析后的权益数组（前端直接渲染） */
  private List<String> benefits;

  /** 0否 1是 */
  private Integer recommend;

  private LocalDateTime createTime;
}