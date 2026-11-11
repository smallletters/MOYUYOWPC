package com.moyuyo.common.dto.subscribe;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自动续费开关请求体。
 * <p>
 * 关键修复：原接口用 {@code @RequestParam Boolean autoRenew}，APP 端调用时未携带任何参数
 * （也未传 body），必然 400。改为请求体接收并显式校验非空。
 */
@Data
public class AutoRenewRequest {

  @NotNull(message = "请指定自动续费状态")
  private Boolean autoRenew;
}
