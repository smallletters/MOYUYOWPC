package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.pet.PetAchievementVO;
import com.moyuyo.common.dto.pet.PetReminderVO;
import com.moyuyo.common.dto.pet.PetVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.GrowthRecordEntity;
import com.moyuyo.dao.entity.PetAchievementEntity;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.PetReminderEntity;
import com.moyuyo.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "宠物档案")
@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
// 方法级校验：@PathVariable 上的 @Positive 才能生效
@Validated
public class PetController {

  private final PetService petService;

  @Operation(summary = "宠物列表")
  @GetMapping
  public Result<List<PetVO>> list() {
    return Result.success(petService.listByUserId(UserContextHolder.getUserId()));
  }

  @Operation(summary = "宠物详情")
  @GetMapping("/{id}")
  public Result<PetVO> getById(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id) {
    return Result.success(petService.getPetDetail(id, UserContextHolder.getUserId()));
  }

  @Operation(summary = "创建宠物")
  @PostMapping
  public Result<PetEntity> create(@RequestBody PetEntity pet) {
    return Result.success(petService.createPet(UserContextHolder.getUserId(), pet));
  }

  @Operation(summary = "更新宠物")
  @PutMapping("/{id}")
  public Result<PetEntity> update(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id, @RequestBody PetEntity pet) {
    pet.setId(id);
    return Result.success(petService.updatePet(UserContextHolder.getUserId(), pet));
  }

  @Operation(summary = "删除宠物")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id) {
    petService.deletePet(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "成长记录列表")
  @GetMapping("/{id}/records")
  public Result<List<GrowthRecordEntity>> getRecords(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id) {
    return Result.success(petService.getGrowthRecords(id, UserContextHolder.getUserId()));
  }

  @Operation(summary = "创建成长记录")
  @PostMapping("/{id}/records")
  public Result<GrowthRecordEntity> createRecord(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id, @RequestBody GrowthRecordEntity record) {
    record.setPetId(id);
    return Result.success(petService.createGrowthRecord(UserContextHolder.getUserId(), record));
  }

  @Operation(summary = "护理提醒列表")
  @GetMapping("/{id}/reminders")
  public Result<List<PetReminderVO>> getReminders(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id) {
    return Result.success(petService.getReminders(id, UserContextHolder.getUserId()));
  }

  @Operation(summary = "更新提醒")
  @PutMapping("/{id}/reminders/{reminderId}")
  public Result<PetReminderEntity> updateReminder(
      @PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id,
      @PathVariable @Positive(message = "提醒 ID 必须为正整数") Long reminderId,
      @RequestBody PetReminderEntity reminder) {
    reminder.setId(reminderId);
    reminder.setPetId(id);
    return Result.success(petService.updateReminder(UserContextHolder.getUserId(), reminder));
  }

  @Operation(summary = "成就列表")
  @GetMapping("/{id}/achievements")
  public Result<List<PetAchievementVO>> getAchievements(@PathVariable @Positive(message = "宠物 ID 必须为正整数") Long id) {
    List<PetAchievementEntity> entities = petService.getAchievements(id, UserContextHolder.getUserId());
    List<PetAchievementVO> vos = entities.stream().map(e -> {
      PetAchievementVO vo = new PetAchievementVO();
      org.springframework.beans.BeanUtils.copyProperties(e, vo);
      return vo;
    }).collect(Collectors.toList());
    return Result.success(vos);
  }
}
