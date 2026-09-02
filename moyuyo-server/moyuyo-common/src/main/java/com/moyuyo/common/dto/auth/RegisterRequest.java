package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 * <p>
 * 字段约束:
 * <ul>
 *   <li>{@code email} / {@code phone}: 业务级必填其一,在 Service 层强校验;
 *       这里不再加 {@code @Email / @NotBlank} 因为 bean validation 会同时校验,导致
 *       "phone-only 注册用户被 @Email 拦截报 400"。</li>
 *   <li>{@code smsCode}: phone-only 路径必填,用于校验验证码。</li>
 * </ul>
 */
@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "邮箱（与 phone 二选一）", example = "user@example.com")
    private String email;

    @Schema(description = "手机号（与 email 二选一），必须含国家区号", example = "+12025550123")
    private String phone;

    @Schema(description = "手机号路径下必填的 6 位短信验证码", example = "123456")
    private String smsCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需8-64位")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,64}$",
         message = "密码需包含大小写字母和数字")
    @Schema(description = "密码（需包含大小写字母和数字）", example = "MyPassword123")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长50个字符")
    @Schema(description = "昵称", example = "John")
    private String nickname;

    @Schema(description = "国家 ISO 3166-1 alpha-2", example = "US")
    private String country;

    @Schema(description = "区号冗余字段（phone 已含区号时也允许传，便于后端处理）", example = "+1")
    private String countryCode;

    @Schema(description = "宠物偏好:DOG / CAT / OTHER", example = "DOG")
    private String petType;

    @Schema(description = "是否同意营销邮件", example = "false")
    private Boolean marketingOptIn;
}