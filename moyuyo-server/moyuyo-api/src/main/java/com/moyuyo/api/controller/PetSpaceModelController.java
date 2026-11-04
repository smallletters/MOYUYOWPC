package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;
import com.moyuyo.service.admin.AdminPetSpaceModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 端 - 宠物 3D 空间模型配置
 * <p>
 * 仅返回已启用模型；APP 端按 modelKey 对应到房间场景 / 角色模型。
 */
@Tag(name = "宠物3D空间模型")
@RestController
@RequestMapping("/api/v1/pet-space")
@RequiredArgsConstructor
public class PetSpaceModelController {

    private final AdminPetSpaceModelService adminPetSpaceModelService;

    @Operation(summary = "查询已启用的 3D 空间模型")
    @GetMapping("/models")
    public Result<List<Map<String, Object>>> listEnabled() {
        List<Map<String, Object>> result = adminPetSpaceModelService.listEnabled().stream()
                .map(this::toView)
                .toList();
        return Result.success(result);
    }

    /** 只暴露 APP 渲染所需字段，避免把后台备注 / 状态等内部信息带出去 */
    private Map<String, Object> toView(PetSpaceModelEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("modelKey", entity.getModelKey());
        item.put("displayName", entity.getDisplayName());
        item.put("fileUrl", entity.getFileUrl());
        item.put("fileFormat", entity.getFileFormat());
        item.put("fileSize", entity.getFileSize());
        item.put("version", entity.getVersion());
        return item;
    }
}
