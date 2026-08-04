package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 A/B 测试创建/更新请求
 * 创建时 id 为空,更新时 id 由路径参数提供
 */
@Data
public class AbTestRequest {

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
  private BigDecimal groupAConvRate;

  /** B 组转化率 */
  private BigDecimal groupBConvRate;
}
