package com.moyuyo.service;

import com.moyuyo.common.dto.pet.PetCareSummaryVO;
import com.moyuyo.common.dto.pet.PetReminderVO;
import com.moyuyo.common.dto.pet.PetVO;
import com.moyuyo.dao.entity.GrowthRecordEntity;
import com.moyuyo.dao.entity.PetAchievementEntity;
import com.moyuyo.dao.entity.PetEntity;
import com.moyuyo.dao.entity.PetReminderEntity;

import java.util.List;

public interface PetService {

  List<PetVO> listByUserId(Long userId);

  PetVO getPetDetail(Long petId, Long userId);

  PetEntity createPet(Long userId, PetEntity pet);

  PetEntity updatePet(Long userId, PetEntity pet);

  /** 更新宠物状态（ACTIVE/DIED/ACCIDENT），用于「去世/意外/恢复正常」标记 */
  PetEntity updatePetStatus(Long userId, Long petId, String status);

  void deletePet(Long petId, Long userId);

  List<GrowthRecordEntity> getGrowthRecords(Long petId, Long userId);

  GrowthRecordEntity createGrowthRecord(Long userId, GrowthRecordEntity record);

  void deleteGrowthRecord(Long userId, Long petId, Long recordId);

  List<PetReminderVO> getReminders(Long petId, Long userId);

  PetReminderEntity updateReminder(Long userId, PetReminderEntity reminder);

  /** 护理聚合摘要：最近记录 + 提醒配置，一次返回主页卡片所需全部数据 */
  List<PetCareSummaryVO> getCareSummary(Long petId, Long userId);

  List<PetAchievementEntity> getAchievements(Long petId, Long userId);
}
