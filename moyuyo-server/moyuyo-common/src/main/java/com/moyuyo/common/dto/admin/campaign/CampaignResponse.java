package com.moyuyo.common.dto.admin.campaign;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台营销活动视图对象(列表/详情基础信息)
 */
@Data
public class CampaignResponse {

  private Long id;

  /** 活动名称 */
  private String name;

  /** 活动类型:满减/折扣/秒杀/拼团 */
  private String type;

  /** 活动状态:ACTIVE / UPCOMING / ENDED */
  private String status;

  /** 活动描述 */
  private String description;

  /** 开始时间(前端展示为 yyyy-MM-dd 字符串) */
  private String startDate;

  /** 结束时间(前端展示为 yyyy-MM-dd 字符串) */
  private String endDate;

  /** 参与人数 */
  private Integer participants;

  /** GMV */
  private BigDecimal gmv;

  /** 预算 */
  private BigDecimal budget;

  /** 成本 */
  private BigDecimal cost;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;
}
