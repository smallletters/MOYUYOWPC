package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;
import com.moyuyo.dao.admin.entity.PetSpaceModelPurposeEntity;
import com.moyuyo.service.admin.AdminPetSpaceModelPurposeService;
import com.moyuyo.service.admin.AdminPetSpaceModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - APP 宠物 3D 空间模型
 * <p>
 * 用于维护房间场景、静态角色、行走角色三类模型；同一用途只允许一个启用版本。
 */
@Tag(name = "管理后台 - 3D空间模型")
@RestController
@RequestMapping("/api/admin/pet-space-model")
@RequiredArgsConstructor
public class AdminPetSpaceModelController {

    private final AdminPetSpaceModelService adminPetSpaceModelService;
    private final AdminPetSpaceModelPurposeService adminPetSpaceModelPurposeService;

    @Operation(summary = "模型列表")
    @GetMapping("/list")
    public Result<List<PetSpaceModelEntity>> list(
            @RequestParam(required = false) String modelKey,
            @RequestParam(required = false) String status) {
        return Result.success(adminPetSpaceModelService.list(modelKey, status));
    }

    // ==================== 模型用途 ====================

    @Operation(summary = "用途列表（含停用）")
    @GetMapping("/purposes")
    public Result<List<PetSpaceModelPurposeEntity>> listPurposes() {
        return Result.success(adminPetSpaceModelPurposeService.listAll());
    }

    @Operation(summary = "新增用途")
    @PostMapping("/purposes/create")
    public Result<Map<String, Object>> createPurpose(@RequestBody PetSpaceModelPurposeEntity body) {
        if (body == null) {
            return Result.badRequest("参数不能为空");
        }
        adminPetSpaceModelPurposeService.create(body);
        return Result.success(Map.of("id", body.getId(), "message", "用途创建成功"));
    }

    @Operation(summary = "更新用途")
    @PutMapping("/purposes/update")
    public Result<Map<String, Object>> updatePurpose(@RequestBody PetSpaceModelPurposeEntity body) {
        if (body == null || body.getId() == null) {
            return Result.badRequest("参数错误：id 不能为空");
        }
        adminPetSpaceModelPurposeService.update(body);
        return Result.success(Map.of("id", body.getId(), "message", "用途更新成功"));
    }

    @Operation(summary = "删除用途")
    @DeleteMapping("/purposes/{id}")
    public Result<Map<String, Object>> deletePurpose(@PathVariable Long id) {
        adminPetSpaceModelPurposeService.delete(id);
        return Result.success(Map.of("id", id, "message", "用途删除成功"));
    }

    @Operation(summary = "新增模型")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody PetSpaceModelEntity body) {
        if (body == null) {
            return Result.badRequest("参数不能为空");
        }
        adminPetSpaceModelService.create(body);
        return Result.success(Map.of("id", body.getId(), "message", "模型创建成功"));
    }

    @Operation(summary = "更新模型")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody PetSpaceModelEntity body) {
        if (body == null || body.getId() == null) {
            return Result.badRequest("参数错误：id 不能为空");
        }
        adminPetSpaceModelService.update(body);
        return Result.success(Map.of("id", body.getId(), "message", "模型更新成功"));
    }

    @Operation(summary = "启用模型")
    @PostMapping("/{id}/enable")
    public Result<Map<String, Object>> enable(@PathVariable Long id) {
        adminPetSpaceModelService.enable(id);
        return Result.success(Map.of("id", id, "message", "模型已启用"));
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminPetSpaceModelService.delete(id);
        return Result.success(Map.of("id", id, "message", "模型删除成功"));
    }
}
