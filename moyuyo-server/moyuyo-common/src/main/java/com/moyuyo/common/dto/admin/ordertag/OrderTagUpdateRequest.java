package com.moyuyo.common.dto.admin.ordertag;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新订单标签请求
 */
@Data
public class OrderTagUpdateRequest {

  @NotNull(message = "标签ID不能为空")
  private Long id;

  private String name;

  private String color;

  private String description;

  private Integer sortOrder;

  private Integer enabled;
}
