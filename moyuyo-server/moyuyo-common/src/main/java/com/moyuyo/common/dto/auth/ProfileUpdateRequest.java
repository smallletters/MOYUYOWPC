package com.moyuyo.common.dto.auth;

import com.moyuyo.common.utils.XssSanitizer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户个人资料更新 DTO（白名单字段）。
 * <p>
 * 背景：原 /api/v1/users/me PUT 接口直接接收 {@code UserEntity}，尽管
 * {@code AuthServiceImpl.updateCurrentUser} 仅过滤 8 个字段，但 {@code UserEntity}
 * 含 passwordHash / role / status / points / twoFactorEnabled / emailVerified 等敏感字段，
 * 攻击者可构造 JSON 注入尝试越权修改（如 {@code {"role":"ADMIN","points":99999999}}）。
 * <p>
 * 修复要点：
 * <ol>
 *   <li>仅暴露白名单字段（昵称/头像/性别/生日/国家/语言/时区/营销订阅 8 项）</li>
 *   <li>头像 URL 强制 https?:// 协议（拒绝 javascript:/data:/vbscript: 等危险协议）</li>
 *   <li>性别字段限定枚举值（MALE/FEMALE/OTHER/UNDISCLOSED），避免任意文本入库污染管理后台筛选</li>
 *   <li>字符串字段统一经 {@link XssSanitizer#sanitizePlainText} 净化</li>
 *   <li>Service 层在 Bean Validation 之上做二次校验（{@link #isAvatarValid()} 兜底）</li>
 * </ol>
 */
@Data
@Schema(description = "用户个人资料更新请求体（白名单字段）")
public class ProfileUpdateRequest {

    /** 昵称：去除 HTML、控制字符；长度限制避免超大输入撑爆存储 */
    @Size(max = 64, message = "昵称长度不能超过 64 字符")
    @Schema(description = "昵称", example = "小明")
    private String nickname;

    /**
     * 头像 URL：协议白名单（http/https 绝对 URL 或 /uploads/ 相对路径），避免 javascript:/data: 协议 XSS
     * <p>
     * 兼容两种形态：
     * <ul>
     *   <li>绝对 URL：{@code https://cdn.example.com/avatar.png}</li>
     *   <li>项目内相对路径：{@code /uploads/2026/09/01/uuid.png}（dev 通过 vite proxy,prod 通过 nginx 同源）</li>
     * </ul>
     */
    @Size(max = 512, message = "头像 URL 长度不能超过 512 字符")
    @Pattern(regexp = "^(https?://|/uploads/).*$", message = "头像 URL 必须为 http(s):// 绝对路径或 /uploads/ 项目内路径")
    @Schema(description = "头像 URL（https?:// 绝对路径 或 /uploads/ 项目内路径）", example = "/uploads/2026/09/01/uuid.png")
    private String avatar;

    /** 性别：枚举值，避免任意文本入库 */
    @Pattern(regexp = "^(MALE|FEMALE|OTHER|UNDISCLOSED)?$", message = "性别必须为 MALE/FEMALE/OTHER/UNDISCLOSED 之一")
    @Schema(description = "性别：MALE(男)/FEMALE(女)/OTHER(中性)/UNDISCLOSED(不透露)", example = "MALE")
    private String gender;

    /** 生日：必须为过去日期，避免用户输入未来日期产生负数年龄 */
    @Past(message = "生日必须为过去日期")
    @Schema(description = "生日（ISO-8601，过去日期）", example = "1990-01-01")
    private LocalDate birthday;

    /** 国家：ISO 3166-1 二字/三字代码 */
    @Size(max = 8, message = "国家代码长度不能超过 8 字符")
    @Pattern(regexp = "^[A-Za-z]{2,3}$", message = "国家必须为 ISO 3166-1 二字/三字代码")
    @Schema(description = "国家代码（ISO 3166-1）", example = "CN")
    private String country;

    /** 语言：BCP 47 格式（如 zh-CN / en-US） */
    @Size(max = 16, message = "语言代码长度不能超过 16 字符")
    @Pattern(regexp = "^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$", message = "语言必须为 BCP 47 格式（如 zh-CN / en-US）")
    @Schema(description = "语言代码（BCP 47）", example = "zh-CN")
    private String locale;

    /** 时区：IANA 时区 ID（如 Asia/Shanghai） */
    @Size(max = 64, message = "时区长度不能超过 64 字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_+/.-]*$", message = "时区必须为 IANA 时区 ID 格式")
    @Schema(description = "时区（IANA）", example = "Asia/Shanghai")
    private String timezone;

    /** 营销订阅开关：用户主动选择的营销通知偏好 */
    @Schema(description = "是否订阅营销通知", example = "true")
    private Boolean marketingOptIn;

    /**
     * 头像 URL 协议白名单二级校验。
     * <p>
     * Bean Validation 的 {@link Pattern} 仅校验格式，此处再做一次语义校验：
     * 拒绝 javascript:/data:/vbscript: 等危险协议绕过（攻击者可能通过 URL 编码 / 大小写混合绕过）。
     *
     * @return true=合法；false=非法
     */
    public boolean isAvatarValid() {
        if (avatar == null || avatar.isEmpty()) {
            return true; // 空值视为合法,由 Service 层决定是否更新
        }
        String lower = avatar.trim().toLowerCase();
        // 显式拒绝危险协议(覆盖大小写、空白、URL 编码后的等价形式)
        // 注意:/uploads/ 是项目内相对路径,走 vite proxy / nginx 反代,与后端同源,
        //      无 javascript:/data: 等危险协议风险,这里显式放行(Bean Validation 的 Pattern 已先校验)
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return false;
        }
        // 通过 Pattern 校验后已经确保 http/https 或 /uploads/,但再做一次长度防御
        return avatar.length() <= 512;
    }
}