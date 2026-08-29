package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "重置密码请求")
public class ResetPasswordRequest {

    @NotBlank(message = "重置验证码不能为空")
    @Size(min = 6, max = 6, message = "重置验证码必须为 6 位数字")
    @Pattern(regexp = "^\\d{6}$", message = "重置验证码必须为 6 位数字")
    @Schema(description = "邮件中的 6 位重置验证码", example = "834721")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需8-64位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,64}$",
             message = "密码需包含大小写字母和数字")
    @Schema(description = "新密码", example = "MyNewPass456")
    private String newPassword;
}
