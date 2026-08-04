package com.moyuyo.common.dto.admin.campaign;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 A/B 测试视图对象(列表/详情)
 */
@Data
public class AbTestResponse {

  private Long id;

  /** 测试名称 */
  private String name;

  /** 测试状态:RUNNING / COMPLETED */
  private String status;

  /** 测试描述 */
  private String description;

  /** A 组访客数 */
  private Integer groupAVisitors;

  /** B 组访客数 */
  private Integer groupBVisitors;

  /** A 组转化率 */
  private java.math.BigDecimal groupAConvRate;

  /** B 组转化率 */
  private java.math.BigDecimal groupBConvRate;

  /** 开始时间 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime startTime;
}
