package com.moyuyo.common.dto.admin.ordertag;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建订单标签请求
 */
@Data
public class OrderTagCreateRequest {

  @NotBlank(message = "标签名称不能为空")
  private String name;

  private String color;

  private String description;

  private Integer sortOrder;

  /** 是否启用:1启用 0禁用,默认启用 */
  private Integer enabled;
}
