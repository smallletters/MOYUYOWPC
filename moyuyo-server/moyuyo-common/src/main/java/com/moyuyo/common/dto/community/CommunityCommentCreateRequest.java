package com.moyuyo.common.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 社区评论请求 DTO
 * <p>
 * P2 优化：{@code CommunityController} 评论接口的 {@code @RequestParam} 入参统一收敛为 DTO,
 * 强制 {@code content} 长度约束，避免空评论与超长评论。
 */
@Data
public class CommunityCommentCreateRequest {

    @Schema(description = "评论内容（1~500 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 500, message = "评论内容长度必须在 1~500 字符之间")
    private String content;

    @Schema(description = "父评论 ID（可选，二级评论用）")
    private Long parentId;
}