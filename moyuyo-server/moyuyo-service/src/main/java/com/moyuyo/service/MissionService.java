package com.moyuyo.service;

import com.moyuyo.dao.entity.MissionEntity;
import com.moyuyo.dao.entity.UserMissionEntity;

import java.util.List;
import java.util.Map;

public interface MissionService {

  List<MissionEntity> listAllMissions();

  /** 按 daily / weekly / achievement 分组返回（前端期望结构） */
  Map<String, Object> listGroupedMissions(Long userId);

  List<UserMissionEntity> listUserMissions(Long userId);

  void claimReward(Long userId, Long missionId);

  /** 增加任务进度（自动判断是否完成） */
  void incrementProgress(Long userId, Long missionId, int delta);

  Map<String, Object> getMissionStats(Long userId);
}