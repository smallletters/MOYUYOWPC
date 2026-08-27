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

  /**
   * 按任务类型 + 名称关键字匹配第一个匹配任务并累加进度。
   * 用于"分享/签到/购物/笔记/邀请"等无 missionId 上下文的埋点场景；
   * 通过 type 区分 DAILY/WEEKLY/ACHIEVEMENT，避免 name 重名时匹配错任务。
   */
  void incrementByKeyword(Long userId, String type, String keyword, int delta);

  /**
   * 按任务类型 + 名称关键字匹配后，**累加** 指定金额到该任务进度。
   * 用于"累计消费 $500"类需要按数值计算进度的成就任务。
   */
  void accumulateByKeyword(Long userId, String type, String keyword, int delta);

  Map<String, Object> getMissionStats(Long userId);
}