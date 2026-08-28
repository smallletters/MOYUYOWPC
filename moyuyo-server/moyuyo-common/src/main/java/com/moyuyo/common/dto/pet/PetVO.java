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

  private LocalDate birthday;

  private String avatar;

  private Double weight;

  private String notes;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private List<PetAchievementVO> achievements;

  private List<PetSceneVO> scenes;
}
