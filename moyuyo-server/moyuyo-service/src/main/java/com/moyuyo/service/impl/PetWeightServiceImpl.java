package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.PetWeightEntity;
import com.moyuyo.dao.mapper.PetWeightMapper;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.PetWeightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetWeightServiceImpl implements PetWeightService {

  private final PetWeightMapper petWeightMapper;
  // 任务中心埋点：记录宠物体重也代表一次 Pet Hub 互动
  private final MissionService missionService;

  @Override
  public List<PetWeightEntity> listByPetId(Long petId, Long userId) {
    return petWeightMapper.selectList(
        new LambdaQueryWrapper<PetWeightEntity>()
            .eq(PetWeightEntity::getPetId, petId)
            .orderByDesc(PetWeightEntity::getMeasuredAt));
  }

  @Override
  @Transactional
  public PetWeightEntity create(Long petId, Long userId, PetWeightEntity entity) {
    entity.setId(null);
    entity.setPetId(petId);
    if (entity.getUnit() == null) {
      entity.setUnit("kg");
    }
    petWeightMapper.insert(entity);
    log.info("PetWeight created: petId={}, weightId={}", petId, entity.getId());

    // 任务中心埋点：记录体重视为一次 Pet Hub 互动
    try {
      missionService.incrementByKeyword(userId, "DAILY", "Pet Hub", 1);
    } catch (Exception e) {
      log.warn("[pet-weight] trigger mission failed: petId={}, reason={}", petId, e.getMessage());
    }
    return entity;
  }

  @Override
  public List<PetWeightEntity> getChart(Long petId, Long userId) {
    return petWeightMapper.selectList(
        new LambdaQueryWrapper<PetWeightEntity>()
            .eq(PetWeightEntity::getPetId, petId)
            .orderByAsc(PetWeightEntity::getMeasuredAt));
  }
}
