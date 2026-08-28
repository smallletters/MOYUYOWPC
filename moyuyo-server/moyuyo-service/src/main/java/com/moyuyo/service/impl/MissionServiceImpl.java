package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.MissionEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.entity.UserMissionEntity;
import com.moyuyo.dao.mapper.MissionMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.dao.mapper.UserMissionMapper;
import com.moyuyo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionServiceImpl implements MissionService {

  private final MissionMapper missionMapper;
  private final UserMissionMapper userMissionMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;

  @Override
  public List<MissionEntity> listAllMissions() {
    return missionMapper.selectList(
        new LambdaQueryWrapper<MissionEntity>()
            .eq(MissionEntity::getActive, 1)
            .orderByAsc(MissionEntity::getSortOrder));
  }

  /**
   * 按类型返回任务列表（前端期望结构 {daily, weekly, achievements}）。
   * 同时为首次访问的用户初始化 user_mission 记录。
   */
  @Override
  public Map<String, Object> listGroupedMissions(Long userId) {
    List<MissionEntity> all = listAllMissions();
    ensureUserMissions(userId, all);

    Map<Long, UserMissionEntity> umMap = new HashMap<>();
    for (UserMissionEntity um : userMissionMapper.selectList(
        new LambdaQueryWrapper<UserMissionEntity>().eq(UserMissionEntity::getUserId, userId))) {
      umMap.put(um.getMissionId(), um);
    }

    List<Map<String, Object>> daily = new ArrayList<>();
    List<Map<String, Object>> weekly = new ArrayList<>();
    List<Map<String, Object>> achievements = new ArrayList<>();

    for (MissionEntity m : all) {
      UserMissionEntity um = umMap.get(m.getId());
      Map<String, Object> item = toVo(m, um);
      String type = m.getType() == null ? "" : m.getType().toUpperCase();
      if ("DAILY".equals(type)) {
        daily.add(item);
      } else if ("WEEKLY".equals(type)) {
        weekly.add(item);
      } else if ("ACHIEVEMENT".equals(type)) {
        achievements.add(item);
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("daily", daily);
    result.put("weekly", weekly);
    result.put("achievements", achievements);
    return result;
  }

  @Override
  public List<UserMissionEntity> listUserMissions(Long userId) {
    return userMissionMapper.selectList(
        new LambdaQueryWrapper<UserMissionEntity>()
            .eq(UserMissionEntity::getUserId, userId));
  }

  /**
   * 增加用户某任务的进度（如签到、浏览、分享后调用）。自动判断是否达成完成。
   * progress 字段累加，当 progress >= target 时标记 completed。
   */
  @Override
  @Transactional
  public void incrementProgress(Long userId, Long missionId, int delta) {
    MissionEntity m = missionMapper.selectById(missionId);
    if (m == null || m.getActive() != 1) {
      return;
    }
    UserMissionEntity um = userMissionMapper.selectOne(
        new LambdaQueryWrapper<UserMissionEntity>()
            .eq(UserMissionEntity::getUserId, userId)
            .eq(UserMissionEntity::getMissionId, missionId));
    if (um == null) {
      um = new UserMissionEntity();
      um.setUserId(userId);
      um.setMissionId(missionId);
      um.setProgress(delta);
      um.setCompleted(m.getTarget() != null && um.getProgress() >= m.getTarget() ? 1 : 0);
      um.setClaimed(0);
      userMissionMapper.insert(um);
    } else if (um.getCompleted() == null || um.getCompleted() != 1) {
      int newProgress = (um.getProgress() == null ? 0 : um.getProgress()) + delta;
      um.setProgress(newProgress);
      if (m.getTarget() != null && newProgress >= m.getTarget()) {
        um.setCompleted(1);
      }
      userMissionMapper.updateById(um);
    }
    log.info("Mission progress inc: userId={}, missionId={}, delta={}", userId, missionId, delta);
  }

  /**
   * 按 type + 关键字匹配第一个任务并累加进度。
   * type 必须非空（DAILY/WEEKLY/ACHIEVEMENT），keyword 非空，用于精确定位。
   */
  @Override
  @Transactional
  public void incrementByKeyword(Long userId, String type, String keyword, int delta) {
    if (userId == null || type == null || keyword == null) return;
    MissionEntity mission = missionMapper.selectOne(
        new LambdaQueryWrapper<MissionEntity>()
            .eq(MissionEntity::getActive, 1)
            .eq(MissionEntity::getType, type.toUpperCase())
            .like(MissionEntity::getName, keyword)
            .last("LIMIT 1"));
    if (mission == null) {
      log.debug("[mission] no active mission matched type={}, keyword={}", type, keyword);
      return;
    }
    incrementProgress(userId, mission.getId(), delta);
  }

  /**
   * 累加金额型任务进度（不重置，仅累加；适合"累计消费 $500"）。
   */
  @Override
  @Transactional
  public void accumulateByKeyword(Long userId, String type, String keyword, int delta) {
    incrementByKeyword(userId, type, keyword, delta);
  }

  @Override
  @Transactional
  public void claimReward(Long userId, Long missionId) {
    MissionEntity mission = missionMapper.selectById(missionId);
    if (mission == null) {
      throw new IllegalArgumentException("任务不存在");
    }

    UserMissionEntity userMission = userMissionMapper.selectOne(
        new LambdaQueryWrapper<UserMissionEntity>()
            .eq(UserMissionEntity::getUserId, userId)
            .eq(UserMissionEntity::getMissionId, missionId));

    if (userMission == null) {
      throw new IllegalArgumentException("未领取该任务");
    }
    if (userMission.getCompleted() != 1) {
      throw new IllegalStateException("任务未完成，无法领取奖励");
    }
    if (userMission.getClaimed() == 1) {
      throw new IllegalStateException("奖励已领取");
    }

    userMission.setClaimed(1);
    userMissionMapper.updateById(userMission);

    awardPoints(userId, mission.getPoints() == null ? 0 : mission.getPoints(),
        "MISSION", String.valueOf(missionId),
        "任务奖励：" + mission.getName());

    log.info("Mission reward claimed: userId={}, missionId={}, points={}", userId, missionId, mission.getPoints());
  }

  @Override
  public Map<String, Object> getMissionStats(Long userId) {
    List<UserMissionEntity> userMissions = listUserMissions(userId);

    // 今日已获积分：直接从 points_log 正向流水聚合，与签到页 / 任务奖励发放共用同一数据源
    LocalDate today = LocalDate.now();
    Integer todayPointsBoxed = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .gt(PointsLogEntity::getChangeValue, 0)
            .ge(PointsLogEntity::getCreatedAt, today.atStartOfDay()))
        .stream()
        .mapToInt(PointsLogEntity::getChangeValue)
        .sum();
    int todayPoints = todayPointsBoxed == null ? 0 : todayPointsBoxed;

    long dailyDone = userMissions.stream()
        .filter(um -> um.getCompleted() != null && um.getCompleted() == 1)
        .map(um -> missionMapper.selectById(um.getMissionId()))
        .filter(m -> m != null && "DAILY".equalsIgnoreCase(m.getType()))
        .count();

    List<MissionEntity> allDaily = missionMapper.selectList(
        new LambdaQueryWrapper<MissionEntity>()
            .eq(MissionEntity::getActive, 1)
            .eq(MissionEntity::getType, "DAILY"));
    long dailyTotal = allDaily.size();

    // 连续签到天数：从 points_log(CHECKIN) 倒推，与签到页 calculateStreak 保持一致
    int streak = calculateCheckinStreak(userId, today);

    Map<String, Object> stats = new HashMap<>();
    stats.put("todayPoints", todayPoints);
    stats.put("dailyDone", dailyDone);
    stats.put("dailyTotal", dailyTotal);
    stats.put("streak", streak);
    return stats;
  }

  /**
   * 从今天往前数连续 CHECKIN 流水天数（包含今天如果已签）。
   * 与 check-in.vue calculateStreak 算法一致。
   */
  private int calculateCheckinStreak(Long userId, LocalDate today) {
    List<PointsLogEntity> logs = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>()
            .eq(PointsLogEntity::getUserId, userId)
            .eq(PointsLogEntity::getType, "CHECKIN"));
    if (logs.isEmpty()) {
      return 0;
    }
    // 仅保留日期不重复的 CHECKIN 日（一天多次只算 1 次）
    java.util.Set<String> dateSet = new java.util.HashSet<>();
    for (PointsLogEntity l : logs) {
      if (l.getCreatedAt() == null) continue;
      LocalDate d = l.getCreatedAt().toLocalDate();
      dateSet.add(d.toString());
    }
    int streak = 0;
    LocalDate cursor = today;
    while (dateSet.contains(cursor.toString())) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }

  /**
   * 用户首次查询任务时为每个任务创建一条 user_mission 记录（progress=0, completed=0, claimed=0）。
   */
  private void ensureUserMissions(Long userId, List<MissionEntity> all) {
    if (all.isEmpty()) {
      return;
    }
    List<UserMissionEntity> existing = userMissionMapper.selectList(
        new LambdaQueryWrapper<UserMissionEntity>().eq(UserMissionEntity::getUserId, userId));
    if (!existing.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (MissionEntity m : all) {
      UserMissionEntity um = new UserMissionEntity();
      um.setUserId(userId);
      um.setMissionId(m.getId());
      um.setProgress(0);
      um.setCompleted(0);
      um.setClaimed(0);
      um.setCreateTime(now);
      userMissionMapper.insert(um);
    }
  }

  private Map<String, Object> toVo(MissionEntity m, UserMissionEntity um) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", m.getId());
    map.put("name", m.getName());
    map.put("description", m.getDescription());
    map.put("icon", m.getIcon());
    map.put("points", m.getPoints() == null ? 0 : m.getPoints());
    map.put("target", m.getTarget() == null ? 1 : m.getTarget());
    int progress = um == null || um.getProgress() == null ? 0 : um.getProgress();
    int completed = um == null || um.getCompleted() == null ? 0 : um.getCompleted();
    int claimed = um == null || um.getClaimed() == null ? 0 : um.getClaimed();
    map.put("done", progress);
    map.put("total", m.getTarget() == null ? 1 : m.getTarget());
    map.put("progress", progress);
    map.put("completed", completed);
    map.put("claimed", claimed);
    map.put("earned", claimed == 1);
    // 任务跳转 action 类型（前端不再用 name.includes 软匹配，硬编码 enum 路由）
    map.put("actionType", resolveActionType(m.getName()));
    // 兼容前端状态文案
    if (claimed == 1) {
      map.put("statusText", "已领取");
    } else if (completed == 1) {
      map.put("statusText", "可领取");
    } else if (progress > 0) {
      map.put("statusText", "进行中");
    } else {
      map.put("statusText", "未完成");
    }
    return map;
  }

  /**
   * 把任务 name 映射成 actionType（前端按 actionType 路由跳转）。
   * 已知动作类型（与前端 mission-center.vue 中的 ActionMap 对齐）：
   *   - BROWSE_PRODUCTS 浏览商品 -> 首页
   *   - CHECKIN_DAILY    每日签到 -> 签到页
   *   - SHARE_PRODUCT    分享商品 -> 分享页
   *   - PET_HUB_INTERACT Pet Hub 互动 -> pet tab
   *   - PURCHASE_ORDER   下单/购物 -> 首页
   *   - POST_COMMUNITY   发社区笔记 -> 社区 tab
   *   - INVITE_FRIEND    邀请好友 -> 邀请页
   * <p>
   * 匹配顺序：先精确 equals，再按关键词 fallback，避免误匹配。
   * name 改变不会影响跳转逻辑（前端按 actionType 路由），后端可自由改文案。
   */
  private String resolveActionType(String name) {
    if (name == null) return "UNKNOWN";
    String n = name.trim();
    // 精确匹配优先
    if (n.equals("每日签到") || n.equalsIgnoreCase("CHECKIN")) return "CHECKIN_DAILY";
    if (n.equals("邀请好友") || n.equalsIgnoreCase("INVITE")) return "INVITE_FRIEND";
    if (n.equals("分享商品") || n.equalsIgnoreCase("SHARE")) return "SHARE_PRODUCT";
    if (n.equals("Pet Hub 互动") || n.equalsIgnoreCase("PET_HUB")) return "PET_HUB_INTERACT";
    if (n.equals("发布社区笔记") || n.equals("发布 1 条社区笔记") || n.equalsIgnoreCase("POST_NOTE")) return "POST_COMMUNITY";
    // 关键词 fallback（基于历史文案）
    if (n.contains("签到")) return "CHECKIN_DAILY";
    if (n.contains("邀请")) return "INVITE_FRIEND";
    if (n.contains("分享")) return "SHARE_PRODUCT";
    if (n.toLowerCase().contains("pet hub")) return "PET_HUB_INTERACT";
    if (n.contains("笔记") || n.contains("社区")) return "POST_COMMUNITY";
    if (n.contains("浏览")) return "BROWSE_PRODUCTS";
    if (n.contains("购物") || n.contains("下单") || n.contains("订单")) return "PURCHASE_ORDER";
    return "UNKNOWN";
  }

  /**
   * 直接累加积分到用户表 + 写积分流水，避免 MemberService 循环依赖。
   */
  private void awardPoints(Long userId, int changeValue, String type, String bizNo, String remark) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      return;
    }
    int newPoints = user.getPoints() != null ? user.getPoints() + changeValue : changeValue;
    if (newPoints < 0) {
      log.warn("积分不足 userId={} attempted={} current={}", userId, changeValue, user.getPoints());
      return;
    }
    user.setPoints(newPoints);
    userMapper.updateById(user);

    PointsLogEntity logEntity = new PointsLogEntity();
    logEntity.setUserId(userId);
    logEntity.setChangeValue(changeValue);
    logEntity.setType(type);
    logEntity.setBizNo(bizNo);
    logEntity.setRemark(remark);
    pointsLogMapper.insert(logEntity);
  }
}