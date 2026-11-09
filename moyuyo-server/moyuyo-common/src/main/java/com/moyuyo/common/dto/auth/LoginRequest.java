package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "MyPassword123")
    private String password;

    /**
     * 两步验证码（可选）：账号开启 2FA 时必填。
     * 首次登录不填时服务端会发送验证码并返回 TWO_FACTOR_REQUIRED，
     * 客户端带上验证码重新提交登录即可。
     */
    @Size(min = 6, max = 6, message = "两步验证码必须为 6 位数字")
    @Pattern(regexp = "^\\d{6}$", message = "两步验证码必须为 6 位数字")
    @Schema(description = "6 位两步验证码（开启 2FA 的账号必填）", example = "123456")
    private String twoFactorCode;
}
