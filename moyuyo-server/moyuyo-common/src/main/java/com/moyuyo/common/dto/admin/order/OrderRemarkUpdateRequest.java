package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台更新订单备注请求
 */
@Data
public class OrderRemarkUpdateRequest {

  /** 备注,允许为空表示清空备注 */
  private String remark;
}
