package com.moyuyo.common.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 社区发帖请求 DTO
 * <p>
 * P2 优化：将 {@code CommunityController} 原 {@code @RequestParam} 散参数重构为 DTO + {@code @Valid},
 * 统一约束：
 * <ul>
 *   <li>{@code content} 1~2000 字符：防超长文本撑爆存储与 DB 行大小</li>
 *   <li>{@code topic} ≤ 32 字符：防 topic 字段被滥用做存储型 XSS</li>
 *   <li>{@code images} ≤ 9 张 URL：与前端发布器图片上限对齐</li>
 *   <li>每张图片 URL ≤ 512 字符：防恶意超长 URL</li>
 * </ul>
 */
@Data
public class CommunityPostCreateRequest {

    @Schema(description = "帖子内容（1~2000 字符）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "帖子内容不能为空")
    @Size(min = 1, max = 2000, message = "帖子内容长度必须在 1~2000 字符之间")
    private String content;

    @Schema(description = "话题（≤32 字符，可选）")
    @Size(max = 32, message = "话题长度不能超过 32 字符")
    private String topic;

    @Schema(description = "图片 URL 列表（≤9 张）")
    @Size(max = 9, message = "图片数量不能超过 9 张")
    private List<@Size(max = 512, message = "图片 URL 长度不能超过 512 字符") String> images;
}