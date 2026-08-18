package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.PetAlbumEntity;
import com.moyuyo.service.PetAlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "宠物相册")
@RestController
@RequestMapping("/api/v1/pets/{petId}/albums")
@RequiredArgsConstructor
// 方法级校验：@PathVariable 上的 @Positive 才能生效
@Validated
public class PetAlbumController {

  private final PetAlbumService petAlbumService;

  @Operation(summary = "相片列表")
  @GetMapping
  public Result<List<PetAlbumEntity>> list(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long petId) {
    return Result.success(petAlbumService.listByPetId(petId, UserContextHolder.getUserId()));
  }

  @Operation(summary = "添加相片")
  @PostMapping
  public Result<PetAlbumEntity> create(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long petId, @RequestBody PetAlbumEntity entity) {
    return Result.success(petAlbumService.create(petId, UserContextHolder.getUserId(), entity));
  }

  @Operation(summary = "删除相片")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long petId, @PathVariable @Positive(message = "相片 ID 必须为正整数") Long id) {
    petAlbumService.delete(id, petId, UserContextHolder.getUserId());
    return Result.success();
  }
}
