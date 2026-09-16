package com.moyuyo.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户上传自定义宠物装扮 DTO（V20260916_02 新增）。
 * <p>
 * 前端流程：
 * <ol>
 *   <li>uni.chooseImage 选图 → POST /api/v1/file/upload/image 拿到 imageUrl</li>
 *   <li>POST /api/v1/pets/{petId}/dresser/upload(body = PetOutfitUploadRequest)</li>
 * </ol>
 *
 * 服务端校验：
 * <ul>
 *   <li>imageUrl 协议白名单(http/https//uploads/)，与 ProfileUpdateRequest.isAvatarValid 一致</li>
 *   <li>category 白名单(hat/scarf/clothes/toy/custom)</li>
 *   <li>name 长度限制 100</li>
 * </ul>
 */
@Data
@Schema(description = "用户上传自定义宠物装扮请求")
public class PetOutfitUploadRequest {

    /**
     * 装扮分类：
     * hat(帽子) / scarf(围巾) / clothes(衣服) / toy(玩具) / custom(自定义)
     */
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "^(hat|scarf|clothes|toy|custom)$", message = "分类必须为 hat/scarf/clothes/toy/custom 之一")
    @Schema(description = "装扮分类", example = "hat", allowableValues = {"hat", "scarf", "clothes", "toy", "custom"})
    private String category;

    /**
     * 用户命名的装扮名称（用于装扮列表展示）。
     */
    @Size(max = 100, message = "装扮名称长度不能超过 100")
    @Schema(description = "装扮名称", example = "我的小蝴蝶结")
    private String name;

    /**
     * 图片 URL：来自 /api/v1/file/upload/image 返回的 url 字段。
     * 协议白名单:http:// / https:// / /uploads/
     */
    @NotBlank(message = "图片 URL 不能为空")
    @Size(max = 512, message = "图片 URL 长度不能超过 512")
    @Pattern(regexp = "^(https?://|/uploads/).*$",
            message = "图片 URL 必须为 http(s):// 绝对路径或 /uploads/ 项目内路径")
    @Schema(description = "图片 URL", example = "/uploads/2026/09/16/abc.png")
    private String imageUrl;
}
