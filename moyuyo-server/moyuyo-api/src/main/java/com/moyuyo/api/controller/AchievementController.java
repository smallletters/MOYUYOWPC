package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.AchievementEntity;
import com.moyuyo.dao.entity.UserAchievementEntity;
import com.moyuyo.dao.mapper.AchievementMapper;
import com.moyuyo.dao.mapper.UserAchievementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "成就墙")
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

  private final AchievementMapper achievementMapper;
  private final UserAchievementMapper userAchievementMapper;

  @GetMapping
  public Result<List<AchievementEntity>> all() {
    return Result.success(achievementMapper.selectList(
        new LambdaQueryWrapper<AchievementEntity>()
            .eq(AchievementEntity::getActive, 1)
            .orderByAsc(AchievementEntity::getSortOrder)));
  }

  @GetMapping("/my")
  public Result<Map<String, Object>> myWall() {
    Long userId = UserContextHolder.getUserId();
    List<UserAchievementEntity> mine = userAchievementMapper.selectList(
        new LambdaQueryWrapper<UserAchievementEntity>()
            .eq(UserAchievementEntity::getUserId, userId));
    Set<Long> unlocked = mine.stream()
        .map(UserAchievementEntity::getAchievementId)
        .collect(Collectors.toSet());

    List<AchievementEntity> all = achievementMapper.selectList(
        new LambdaQueryWrapper<AchievementEntity>()
            .eq(AchievementEntity::getActive, 1)
            .orderByAsc(AchievementEntity::getSortOrder));

    List<Map<String, Object>> items = new ArrayList<>();
    for (AchievementEntity a : all) {
      Map<String, Object> item = new HashMap<>();
      item.put("id", a.getId());
      item.put("code", a.getCode());
      item.put("name", a.getName());
      item.put("description", a.getDescription());
      item.put("icon", a.getIcon());
      item.put("badgeImage", a.getBadgeImage());
      item.put("pointsReward", a.getPointsReward());
      item.put("category", a.getCategory());
      item.put("unlocked", unlocked.contains(a.getId()));
      item.put("unlockedAt", mine.stream()
          .filter(u -> u.getAchievementId().equals(a.getId()))
          .findFirst()
          .map(UserAchievementEntity::getUnlockedAt)
          .orElse(null));
      items.add(item);
    }
    Map<String, Object> result = new HashMap<>();
    result.put("total", all.size());
    result.put("unlocked", unlocked.size());
    result.put("items", items);
    return Result.success(result);
  }
}
