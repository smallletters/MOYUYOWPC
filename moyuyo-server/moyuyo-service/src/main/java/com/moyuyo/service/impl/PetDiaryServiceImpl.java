package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.PetDiaryEntity;
import com.moyuyo.dao.mapper.PetDiaryMapper;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.PetDiaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetDiaryServiceImpl implements PetDiaryService {

  private final PetDiaryMapper petDiaryMapper;
  // 任务中心埋点：写宠物日记视为"Pet Hub 互动"
  private final MissionService missionService;

  @Override
  public List<PetDiaryEntity> listByPetId(Long petId, Long userId) {
    return petDiaryMapper.selectList(
        new LambdaQueryWrapper<PetDiaryEntity>()
            .eq(PetDiaryEntity::getPetId, petId)
            .orderByDesc(PetDiaryEntity::getCreateTime));
  }

  @Override
  public PetDiaryEntity getDetail(Long id, Long petId, Long userId) {
    PetDiaryEntity entity = petDiaryMapper.selectById(id);
    if (entity == null || !entity.getPetId().equals(petId)) {
      throw new IllegalArgumentException("日记不存在或无权访问");
    }
    return entity;
  }

  @Override
  @Transactional
  public PetDiaryEntity create(Long petId, Long userId, PetDiaryEntity entity) {
    entity.setId(null);
    entity.setPetId(petId);
    petDiaryMapper.insert(entity);
    log.info("PetDiary created: petId={}, diaryId={}", petId, entity.getId());

    // 任务中心埋点：写宠物日记代表一次 Pet Hub 互动
    try {
      missionService.incrementByKeyword(userId, "DAILY", "Pet Hub", 1);
    } catch (Exception e) {
      log.warn("[pet-diary] trigger mission failed: petId={}, reason={}", petId, e.getMessage());
    }
    return entity;
  }

  @Override
  @Transactional
  public void delete(Long id, Long petId, Long userId) {
    PetDiaryEntity existing = petDiaryMapper.selectById(id);
    if (existing == null || !existing.getPetId().equals(petId)) {
      throw new IllegalArgumentException("日记不存在或无权操作");
    }
    petDiaryMapper.deleteById(id);
    log.info("PetDiary deleted: diaryId={}, petId={}", id, petId);
  }
}
