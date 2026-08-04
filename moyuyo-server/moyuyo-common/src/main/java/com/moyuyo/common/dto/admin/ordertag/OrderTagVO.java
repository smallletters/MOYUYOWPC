package com.moyuyo.common.dto.admin.ordertag;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单标签视图对象(管理端列表/详情返回)
 */
@Data
public class OrderTagVO {

  private Long id;

  private String name;

  private String color;

  private String description;

  private Integer sortOrder;

  /** 是否启用:1启用 0禁用 */
  private Integer enabled;

  /** 状态枚举字符串:ENABLED / DISABLED,便于前端直接展示 */
  private String status;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;

  /** 前端兼容字段,与 createTime 同值 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createdAt;

  /** 标签被订单引用的次数 */
  private Integer usageCount;
}
