package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.PetOutfitUploadRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.PetOutfitEntity;
import com.moyuyo.service.PetDresserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "宠物装扮")
@RestController
@RequestMapping("/api/v1/pets/{petId}/dresser")
@RequiredArgsConstructor
public class PetDresserController {

  private final PetDresserService petDresserService;

  /**
   * 装扮列表（V20260916_02 改造后做过权限过滤）：
   * <ul>
   *   <li>系统默认装扮（user_id=NULL）：所有用户可见</li>
   *   <li>当前用户上传的自定义形象（user_id=当前用户）：仅 owner 可见</li>
   *   <li>其他用户的自定义形象：不在列表里（隐私保护）</li>
   * </ul>
   */
  @Operation(summary = "装扮列表")
  @GetMapping
  public Result<List<PetOutfitEntity>> list(@PathVariable Long petId) {
    return Result.success(petDresserService.listByPetId(petId, UserContextHolder.getUserId()));
  }

  /**
   * 装备装扮（V20260916_02 改造后加权限校验）：
   * <ul>
   *   <li>系统装扮：任何登录用户可装备</li>
   *   <li>用户装扮：仅 owner 可装备</li>
   * </ul>
   */
  @Operation(summary = "装备装扮")
  @PostMapping("/{id}/equip")
  public Result<Void> equip(@PathVariable Long petId, @PathVariable Long id) {
    petDresserService.equip(id, petId, UserContextHolder.getUserId());
    return Result.success();
  }

  /**
   * 删除装扮（V20260916_02 改造后加权限校验）：
   * <ul>
   *   <li>系统装扮：禁止删除（受保护）</li>
   *   <li>用户装扮：仅 owner 可删除</li>
   * </ul>
   */
  @Operation(summary = "删除装扮")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long petId, @PathVariable Long id) {
    petDresserService.delete(id, petId, UserContextHolder.getUserId());
    return Result.success();
  }

  /**
   * 用户上传自定义装扮形象（V20260916_02 新增）。
   * <p>
   * 前端流程：
   * <ol>
   *   <li>uni.chooseImage 选图 → POST /api/v1/file/upload/image → 拿到 imageUrl</li>
   *   <li>POST /api/v1/pets/{petId}/dresser/upload(body: {category, name, imageUrl})</li>
   * </ol>
   * <p>
   * 服务端校验 petId 归属 + imageUrl 协议白名单（与头像上传策略一致）。
   * <p>
   * APP 更新不会移除用户上传的形象：写入 mo_pet_outfit(user_id=当前用户)，
   * 列表接口按 user_id 过滤后稳定返回。
   */
  @Operation(summary = "上传自定义装扮形象")
  @PostMapping("/upload")
  public Result<PetOutfitEntity> upload(@PathVariable Long petId,
                                        @Valid @RequestBody PetOutfitUploadRequest req) {
    Long userId = UserContextHolder.getUserId();
    PetOutfitEntity created = petDresserService.uploadOutfit(
            petId, userId, req.getCategory(), req.getName(), req.getImageUrl());
    return Result.success(created);
  }
}
