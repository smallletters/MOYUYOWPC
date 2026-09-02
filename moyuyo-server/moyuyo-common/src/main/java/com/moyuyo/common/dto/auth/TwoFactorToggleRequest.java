package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 2FA 开关请求。
 * <p>
 * 用途：用户在"账号与安全"页面主动开启/关闭两步验证,
 * 服务端更新 {@code mo_user.two_factor_enabled} 字段并清理已发放的 2FA verified 标记,
 * 避免开启前已验证通过的会话一直绕过二次校验。
 */
@Data
@Schema(description = "2FA 开关请求")
public class TwoFactorToggleRequest {

    @NotNull(message = "enabled 不能为空")
    @Schema(description = "是否开启两步验证", example = "true")
    private Boolean enabled;
}