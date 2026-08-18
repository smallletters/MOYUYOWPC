package com.moyuyo.common.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户反馈提交请求 DTO
 * <p>
 * P2 优化：将 {@code FeedbackController} 原 {@code @RequestParam} 散参数重构为 DTO + {@code @Valid},
 * 强制长度与类型校验，避免恶意超长字段触发存储型 XSS / DB 行溢出 / 邮件炸弹。
 */
@Data
public class FeedbackSubmitRequest {

    /** 反馈类型枚举值：BUG / SUGGEST / COMPLAINT / OTHER */
    @Schema(description = "反馈类型（BUG/SUGGEST/COMPLAINT/OTHER）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "反馈类型不能为空")
    @Pattern(regexp = "^(BUG|SUGGEST|COMPLAINT|OTHER)$",
            message = "反馈类型必须是 BUG/SUGGEST/COMPLAINT/OTHER 之一")
    @Size(max = 16, message = "反馈类型长度超限")
    private String type;

    @Schema(description = "反馈内容（10~2000 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "反馈内容不能为空")
    @Size(min = 10, max = 2000, message = "反馈内容长度必须在 10~2000 字符之间")
    private String content;

    @Schema(description = "截图 URL 列表（≤5 张，每张 ≤512 字符）")
    @Size(max = 5, message = "截图数量不能超过 5 张")
    private List<@Size(max = 512, message = "截图 URL 长度不能超过 512 字符") String> images;

    @Schema(description = "联系方式（邮箱或手机号，≤64 字符，可选）")
    @Size(max = 64, message = "联系方式长度不能超过 64 字符")
    private String contact;
}