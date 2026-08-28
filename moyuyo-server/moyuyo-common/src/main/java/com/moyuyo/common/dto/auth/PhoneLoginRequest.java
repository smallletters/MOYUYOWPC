package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 手机号 + 验证码登录请求。
 * 服务端校验成功后：若手机号未注册则自动创建账号（生成随机密码、昵称），
 *   已注册则返回 JWT。
 */
@Data
@Schema(description = "手机验证码登录请求")
public class PhoneLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\+\\d{8,15}$", message = "手机号必须以 + 开头国家区号,后跟 8-15 位数字")
    @Schema(description = "手机号(含国家区号)", example = "+8613800000000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为 6 位数字")
    @Schema(description = "6 位短信验证码", example = "123456")
    private String code;
}