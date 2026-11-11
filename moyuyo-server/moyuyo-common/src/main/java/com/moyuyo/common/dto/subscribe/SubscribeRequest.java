package com.moyuyo.common.dto.subscribe;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订阅请求体。
 * <p>
 * 关键修复：原接口用 {@code @RequestParam Long planId} 接收，而 APP 端以 JSON body
 * 提交 {@code { planId }}，导致线上必然返回 400「缺少必填参数」。统一改为请求体接收。
 */
@Data
public class SubscribeRequest {

  @NotNull(message = "请选择订阅方案")
  private Long planId;
}
