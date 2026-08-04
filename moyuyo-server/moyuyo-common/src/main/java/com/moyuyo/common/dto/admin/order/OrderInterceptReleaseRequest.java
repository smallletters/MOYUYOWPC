package com.moyuyo.common.dto.admin.order;

import lombok.Data;

/**
 * 管理后台解除订单拦截请求
 */
@Data
public class OrderInterceptReleaseRequest {

  /** 解除原因 */
  private String releaseReason;

  /** 操作人,空则默认"系统" */
  private String releaseOperator;
}
