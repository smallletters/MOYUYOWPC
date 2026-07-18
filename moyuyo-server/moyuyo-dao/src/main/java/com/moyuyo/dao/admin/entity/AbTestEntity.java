package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A/B 测试实体
 */
@Data
@TableName("mo_ab_test")
public class AbTestEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 测试名称 */
  private String name;

  /** 测试状态：RUNNING / COMPLETED */
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

  /** 开始时间 */
  private LocalDateTime startTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
