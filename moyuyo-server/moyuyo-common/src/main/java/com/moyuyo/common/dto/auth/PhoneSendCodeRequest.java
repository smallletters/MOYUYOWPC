package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送手机短信验证码请求。
 * 手机号必须带国家区号，如 +8613800000000 / +14155550123。
 */
@Data
@Schema(description = "发送手机短信验证码请求")
public class PhoneSendCodeRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^\\+\\d{8,15}$", message = "手机号必须以 + 开头国家区号,后跟 8-15 位数字")
    @Schema(description = "手机号(含国家区号)", example = "+8613800000000")
    private String phone;

    @Schema(description = "用途: LOGIN / REGISTER / RESET_PASSWORD",
            example = "LOGIN", allowableValues = {"LOGIN", "REGISTER", "RESET_PASSWORD"})
    private String purpose = "LOGIN";
}