package com.moyuyo.common.dto.admin.order;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台打印模板保存请求
 */
@Data
public class PrintTemplateRequest {

  /** 模板 ID（更新时必填，创建时为空） */
  private Long id;

  /** 模板编码:PICK/PACK/SHIP/LABEL/SHIPPING_LABEL */
  @NotBlank(message = "模板编码不能为空")
  private String code;

  /** 模板名称 */
  @NotBlank(message = "模板名称不能为空")
  private String name;

  /** 默认纸张规格 */
  private String paperSize;

  /** 模板说明 */
  private String description;

  /**
   * 模板 HTML 正文（占位符语法 {{order.xxx}} / {{items}} / {{time}} / {{paper}}）。
   * <p>
   * 可空字符串（重置为代码默认模板）。MEDIUMTEXT 列，上限 16MB，足够复杂的电商模板使用。
   */
  private String contentTemplate;

  /** 是否默认模板 */
  private Boolean isDefault;

  /** 展示顺序 */
  private Integer sortOrder;
}