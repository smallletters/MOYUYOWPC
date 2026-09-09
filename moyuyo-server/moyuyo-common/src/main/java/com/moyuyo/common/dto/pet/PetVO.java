package com.moyuyo.common.dto.pet;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PetVO {

  private Long id;

  private Long userId;

  private String name;

  private String type;

  private String species;

  private String breed;

  private String gender;

  /** 宠物状态：ACTIVE(正常)/DIED(去世)/ACCIDENT(意外) */
  private String status;

  private LocalDate birthday;

  /** 加入家庭时间 */
  private LocalDate adoptedAt;

  private String avatar;

  private Double weight;

  private String notes;

  /** 性格标签 */
  private List<String> tags;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private List<PetAchievementVO> achievements;

  private List<PetSceneVO> scenes;
}
