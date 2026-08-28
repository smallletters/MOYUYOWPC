package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.dto.pet.PetAchievementVO;
import com.moyuyo.common.dto.pet.PetReminderVO;
import com.moyuyo.common.dto.pet.PetSceneVO;
import com.moyuyo.common.dto.pet.PetVO;
import com.moyuyo.dao.entity.GrowthRecordEntity;
import com.moyuyo.dao.entity.PetAchievementEntity;
import com.moyuyo.dao.entity.PetAlbumEntity;
import com.moyuyo.dao.entity.PetDiaryEntity;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.PetOutfitEntity;
import com.moyuyo.dao.entity.PetReminderEntity;
import com.moyuyo.dao.entity.PetSceneEntity;
import com.moyuyo.dao.entity.PetWeightEntity;
import com.moyuyo.dao.mapper.GrowthRecordMapper;
import com.moyuyo.dao.mapper.PetAchievementMapper;
import com.moyuyo.dao.mapper.PetAlbumMapper;
import com.moyuyo.dao.mapper.PetDiaryMapper;
import com.moyuyo.dao.mapper.PetMapper;
import com.moyuyo.dao.mapper.PetOutfitMapper;
import com.moyuyo.dao.mapper.PetReminderMapper;
import com.moyuyo.dao.mapper.PetSceneMapper;
import com.moyuyo.dao.mapper.PetWeightMapper;
import com.moyuyo.service.PetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {

  private final PetMapper petMapper;
  private final PetAchievementMapper petAchievementMapper;
  private final PetSceneMapper petSceneMapper;
  private final GrowthRecordMapper growthRecordMapper;
  private final PetReminderMapper petReminderMapper;
  private final PetWeightMapper petWeightMapper;
  private final PetAlbumMapper petAlbumMapper;
  private final PetDiaryMapper petDiaryMapper;
  private final PetOutfitMapper petOutfitMapper;

  @Override
  public List<PetVO> listByUserId(Long userId) {
    List<PetEntity> pets = petMapper.selectList(
        new LambdaQueryWrapper<PetEntity>()
            .eq(PetEntity::getUserId, userId)
            .orderByDesc(PetEntity::getCreatedAt));

    return pets.stream()
        .map(this::convertToPetVO)
        .collect(Collectors.toList());
  }

  @Override
  public PetVO getPetDetail(Long petId, Long userId) {
    PetEntity entity = petMapper.selectById(petId);
    if (entity == null || !entity.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权访问");
    }
    return convertToPetVO(entity);
  }

  @Override
  @Transactional
  public PetEntity createPet(Long userId, PetEntity pet) {
    pet.setId(null);
    pet.setUserId(userId);
    // 前端 picker 直接传"狗狗/猫咪/兔子/鸟类/其他"中文，需映射成 type 枚举值再写入 DB
    pet.setType(deriveType(pet.getSpecies(), pet.getType()));
    petMapper.insert(pet);
    log.info("Pet created: userId={}, petId={}, name={}", userId, pet.getId(), pet.getName());
    return pet;
  }

  @Override
  @Transactional
  public PetEntity updatePet(Long userId, PetEntity pet) {
    PetEntity existing = petMapper.selectById(pet.getId());
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
    pet.setUserId(userId);
    pet.setType(deriveType(pet.getSpecies(), pet.getType()));
    petMapper.updateById(pet);
    return petMapper.selectById(pet.getId());
  }

  /**
   * 把前端传过来的种类归一化为 DB type（DOG / CAT / OTHER）。
   * 兼容两种输入：
   *  1. 前端 species 已是英文枚举（DOG/CAT/...）→ 直接复用
   *  2. 前端 species 是中文（狗狗/猫咪/...）→ 关键词映射
   * 已为 null直接回退 OTHER。
   */
  private String deriveType(String species, String type) {
    if (type != null && !type.isBlank()) {
      String u = type.trim().toUpperCase();
      if (u.equals("DOG") || u.equals("CAT") || u.equals("OTHER")) return u;
    }
    if (species == null) return "OTHER";
    String s = species.trim();
    if (s.isEmpty()) return "OTHER";
    if (s.contains("狗") || s.equalsIgnoreCase("DOG")) return "DOG";
    if (s.contains("猫") || s.equalsIgnoreCase("CAT")) return "CAT";
    return "OTHER";
  }

  @Override
  @Transactional
  public void deletePet(Long petId, Long userId) {
    PetEntity existing = petMapper.selectById(petId);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
    // 级联删除所有关联子表数据，避免孤儿记录和外键约束
    petWeightMapper.delete(new LambdaQueryWrapper<PetWeightEntity>()
        .eq(PetWeightEntity::getPetId, petId));
    petAlbumMapper.delete(new LambdaQueryWrapper<PetAlbumEntity>()
        .eq(PetAlbumEntity::getPetId, petId));
    petDiaryMapper.delete(new LambdaQueryWrapper<PetDiaryEntity>()
        .eq(PetDiaryEntity::getPetId, petId));
    petOutfitMapper.delete(new LambdaQueryWrapper<PetOutfitEntity>()
        .eq(PetOutfitEntity::getPetId, petId));
    petReminderMapper.delete(new LambdaQueryWrapper<PetReminderEntity>()
        .eq(PetReminderEntity::getPetId, petId));
    petSceneMapper.delete(new LambdaQueryWrapper<PetSceneEntity>()
        .eq(PetSceneEntity::getPetId, petId));
    petAchievementMapper.delete(new LambdaQueryWrapper<PetAchievementEntity>()
        .eq(PetAchievementEntity::getPetId, petId));
    growthRecordMapper.delete(new LambdaQueryWrapper<GrowthRecordEntity>()
        .eq(GrowthRecordEntity::getPetId, petId));
    petMapper.deleteById(petId);
    log.info("Pet deleted: petId={}, userId={}", petId, userId);
  }

  @Override
  public List<GrowthRecordEntity> getGrowthRecords(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    return growthRecordMapper.selectList(
        new LambdaQueryWrapper<GrowthRecordEntity>()
            .eq(GrowthRecordEntity::getPetId, petId)
            .orderByDesc(GrowthRecordEntity::getRecordDate));
  }

  @Override
  @Transactional
  public GrowthRecordEntity createGrowthRecord(Long userId, GrowthRecordEntity record) {
    checkPetOwnership(record.getPetId(), userId);
    record.setId(null);
    record.setUserId(userId);
    growthRecordMapper.insert(record);
    log.info("GrowthRecord created: petId={}, userId={}", record.getPetId(), userId);
    return record;
  }

  @Override
  public List<PetReminderVO> getReminders(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    List<PetReminderEntity> entities = petReminderMapper.selectList(
        new LambdaQueryWrapper<PetReminderEntity>()
            .eq(PetReminderEntity::getPetId, petId)
            .orderByAsc(PetReminderEntity::getNextDate));

    return entities.stream()
        .map(this::convertToReminderVO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public PetReminderEntity updateReminder(Long userId, PetReminderEntity reminder) {
    PetReminderEntity existing = petReminderMapper.selectById(reminder.getId());
    if (existing == null) {
      throw new IllegalArgumentException("提醒不存在");
    }
    checkPetOwnership(existing.getPetId(), userId);
    reminder.setPetId(existing.getPetId());
    petReminderMapper.updateById(reminder);
    return petReminderMapper.selectById(reminder.getId());
  }

  @Override
  public List<PetAchievementEntity> getAchievements(Long petId, Long userId) {
    checkPetOwnership(petId, userId);
    return petAchievementMapper.selectList(
        new LambdaQueryWrapper<PetAchievementEntity>()
            .eq(PetAchievementEntity::getPetId, petId)
            .orderByAsc(PetAchievementEntity::getAchievementCode));
  }

  private void checkPetOwnership(Long petId, Long userId) {
    PetEntity entity = petMapper.selectById(petId);
    if (entity == null || !entity.getUserId().equals(userId)) {
      throw new IllegalArgumentException("宠物不存在或无权操作");
    }
  }

  private PetVO convertToPetVO(PetEntity entity) {
    PetVO vo = new PetVO();
    BeanUtils.copyProperties(entity, vo);

    List<PetAchievementEntity> achievements = petAchievementMapper.selectList(
        new LambdaQueryWrapper<PetAchievementEntity>()
            .eq(PetAchievementEntity::getPetId, entity.getId()));

    List<PetSceneEntity> scenes = petSceneMapper.selectList(
        new LambdaQueryWrapper<PetSceneEntity>()
            .eq(PetSceneEntity::getPetId, entity.getId())
            .orderByAsc(PetSceneEntity::getSortOrder));

    vo.setAchievements(convertAchievementList(achievements));
    vo.setScenes(convertSceneList(scenes));
    return vo;
  }

  private List<PetAchievementVO> convertAchievementList(List<PetAchievementEntity> entities) {
    if (entities == null) {
      return Collections.emptyList();
    }
    return entities.stream().map(e -> {
      PetAchievementVO vo = new PetAchievementVO();
      BeanUtils.copyProperties(e, vo);
      return vo;
    }).collect(Collectors.toList());
  }

  private List<PetSceneVO> convertSceneList(List<PetSceneEntity> entities) {
    if (entities == null) {
      return Collections.emptyList();
    }
    return entities.stream().map(e -> {
      PetSceneVO vo = new PetSceneVO();
      BeanUtils.copyProperties(e, vo);
      return vo;
    }).collect(Collectors.toList());
  }

  private PetReminderVO convertToReminderVO(PetReminderEntity entity) {
    PetReminderVO vo = new PetReminderVO();
    BeanUtils.copyProperties(entity, vo);
    return vo;
  }
}
