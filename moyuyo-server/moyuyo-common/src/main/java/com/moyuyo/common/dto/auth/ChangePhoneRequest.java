package com.moyuyo.common.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 已登录用户更换/绑定手机号请求。
 * <p>
 * 业务规则:
 * <ol>
 *   <li>前端必须先调 {@code POST /api/v1/auth/phone/send-code} 传 {@code purpose=CHANGE_PHONE} 拿到验证码,
 *       后端只接受与该 purpose 匹配的验证码(mo_sms_code.purpose=CHANGE_PHONE)</li>
 *   <li>新手机号必须带国家区号(如 +8613800000000 / +14155550123),格式校验与发码接口一致</li>
 *   <li>新手机号不能与现有其他账号的手机号重复(mo_user.phone 已 uk_user_phone 唯一索引)</li>
 *   <li>提交时校验当前登录用户存在且账号未注销</li>
 * </ol>
 * <p>
 * 安全要点:
 * <ul>
 *   <li>不做"校验旧手机号"步骤:旧手机号可能已无法接收短信(换号场景),改为"必须登录才能提交"作为兜底认证</li>
 *   <li>6 位数字验证码由 Bean Validation 兜底格式;Service 层二次校验 failCount 与过期</li>
 * </ul>
 */
@Data
@Schema(description = "更换/绑定手机号请求")
public class ChangePhoneRequest {

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^\\+\\d{8,15}$", message = "手机号必须以 + 开头国家区号,后跟 8-15 位数字")
    @Schema(description = "新手机号(含国家区号)", example = "+8613800000000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为 6 位数字")
    @Schema(description = "6 位数字验证码(purpose=CHANGE_PHONE)", example = "123456")
    private String code;
}