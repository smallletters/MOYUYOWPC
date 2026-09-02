package com.moyuyo.common.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
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

    @Schema(description = "视频 URL（≤1 个，与 images 互斥：发布视频帖子时 images 传空）")
    @Size(max = 512, message = "视频 URL 长度不能超过 512 字符")
    private String video;

    @Schema(description = "视频封面 URL（仅视频帖需要，前端从视频候选帧中选出后上传）")
    @Size(max = 512, message = "封面 URL 长度不能超过 512 字符")
    private String cover;

    @Schema(description = "定时发布时间（可选，null 或过去时间=立即发布；未来时间则进入待发布队列，由定时任务在到点后切换为已发布）")
    private LocalDateTime scheduledAt;
}