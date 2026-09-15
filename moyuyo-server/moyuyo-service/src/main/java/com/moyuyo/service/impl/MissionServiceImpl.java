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
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

  /**
   * 成就任务的周期基准日：成就无周期概念，用固定基准日保证进度永不被重置。
   * 存量数据由 V20260910_02 迁移成同一基准，否则首次触发会被判定为"换周期"而清零累计进度。
   */
  private static final LocalDate ACHIEVEMENT_CYCLE = LocalDate.of(1970, 1, 1);

  private final MissionMapper missionMapper;
  private final UserMissionMapper userMissionMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;
  // 连续签到天数统一由 MemberService 提供，避免任务中心与签到页各算一套
  private final MemberService memberService;

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
   * 自动按周期重置：DAILY 任务跨天后 progress=0；WEEKLY 任务跨周后 progress=0。
   * <p>
   * 事务传播为 REQUIRES_NEW：埋点属于旁路记账，失败时只回滚自身，
   * 不能让外层业务事务（发笔记/记体重/支付）被标记 rollback-only 而一起失败。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void incrementProgress(Long userId, Long missionId, int delta) {
    MissionEntity m = missionMapper.selectById(missionId);
    // active 为 bit(1) 映射的 Integer,需先判空再比较,避免拆箱 NPE
    if (m == null || m.getActive() == null || m.getActive() != 1) {
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
      um.setCycleDate(currentCycleDate(m.getType()));
      insertUserMissionSafely(um);
    } else {
      // 周期过期：自动重置进度（DAILY=换日 / WEEKLY=换周）
      LocalDate todayCycle = currentCycleDate(m.getType());
      if (um.getCycleDate() == null || !um.getCycleDate().equals(todayCycle)) {
        um.setCycleDate(todayCycle);
        um.setProgress(delta);
        um.setCompleted(m.getTarget() != null && delta >= m.getTarget() ? 1 : 0);
        um.setClaimed(0); // 新周期重新可领取
        userMissionMapper.updateById(um);
        log.info("Mission cycle reset: userId={}, missionId={}, newCycle={}", userId, missionId, todayCycle);
      } else if (um.getCompleted() == null || um.getCompleted() != 1) {
        int newProgress = (um.getProgress() == null ? 0 : um.getProgress()) + delta;
        um.setProgress(newProgress);
        if (m.getTarget() != null && newProgress >= m.getTarget()) {
          um.setCompleted(1);
        }
        userMissionMapper.updateById(um);
      }
    }
    log.info("Mission progress inc: userId={}, missionId={}, delta={}", userId, missionId, delta);
  }

  /**
   * 计算任务当前周期基准日期。
   * DAILY: 今天(0点);WEEKLY: 本周一(0点);ACHIEVEMENT: 固定基准日(永不过期)。
   */
  private LocalDate currentCycleDate(String missionType) {
    if ("ACHIEVEMENT".equalsIgnoreCase(missionType)) {
      return ACHIEVEMENT_CYCLE;
    }
    LocalDate today = LocalDate.now();
    if ("WEEKLY".equalsIgnoreCase(missionType)) {
      // ISO 周: Monday=1 ... Sunday=7
      int dayOfWeek = today.getDayOfWeek().getValue();
      return today.minusDays(dayOfWeek - 1L);
    }
    return today;
  }

  /**
   * 按 type + 关键字匹配第一个任务并累加进度。
   * type 必须非空（DAILY/WEEKLY/ACHIEVEMENT），keyword 非空，用于精确定位。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
   * 累加金额型任务进度（不再重置，仅累加；适合"累计消费 $500"）。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void accumulateByKeyword(Long userId, String type, String keyword, int delta) {
    incrementByKeyword(userId, type, keyword, delta);
  }

  /**
   * 把任务进度直接设为指定值（进度行不存在时按需创建）。
   * 用于「连续签到 30 天」这类需要"断签即归零"的任务。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void setProgressByKeyword(Long userId, String type, String keyword, int progress) {
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
    UserMissionEntity um = userMissionMapper.selectOne(
        new LambdaQueryWrapper<UserMissionEntity>()
            .eq(UserMissionEntity::getUserId, userId)
            .eq(UserMissionEntity::getMissionId, mission.getId()));
    if (um != null && um.getClaimed() != null && um.getClaimed() == 1) {
      // 已领取过奖励的进度行保持原状,重置会让其变成"可再次领取"
      return;
    }
    int completed = mission.getTarget() != null && progress >= mission.getTarget() ? 1 : 0;
    LocalDate cycle = currentCycleDate(mission.getType());
    if (um == null) {
      um = new UserMissionEntity();
      um.setUserId(userId);
      um.setMissionId(mission.getId());
      um.setProgress(progress);
      um.setCompleted(completed);
      um.setClaimed(0);
      um.setCycleDate(cycle);
      insertUserMissionSafely(um);
    } else {
      um.setProgress(progress);
      um.setCompleted(completed);
      um.setCycleDate(cycle);
      userMissionMapper.updateById(um);
    }
    log.info("Mission progress set: userId={}, missionId={}, progress={}", userId, mission.getId(), progress);
  }

  /**
   * 插入任务进度行。并发首次触发时唯一索引 uk_user_mission 会拦下重复插入，
   * 这里吞掉并返回 false：本次埋点放弃即可，进度由并发的另一次请求推进，
   * 避免把 DuplicateKeyException 抛给业务调用方（签到/分享等埋点外层没有兜底）。
   */
  private boolean insertUserMissionSafely(UserMissionEntity um) {
    try {
      userMissionMapper.insert(um);
      return true;
    } catch (DuplicateKeyException e) {
      log.debug("[mission] duplicate progress row, skip insert: userId={}, missionId={}",
          um.getUserId(), um.getMissionId());
      return false;
    }
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
    // 周期校验:上一周期"已完成未领取"的记录不允许跨周期补领
    // (展示层同样按新周期渲染为未完成,这里做接口层兜底,防止直接调接口刷奖励)
    LocalDate nowCycle = currentCycleDate(mission.getType());
    if (userMission.getCycleDate() == null || !nowCycle.equals(userMission.getCycleDate())) {
      throw new IllegalStateException("任务已过周期，无法领取奖励");
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
        .filter(um -> {
          MissionEntity m = missionMapper.selectById(um.getMissionId());
          // 今日 DAILY 任务：进度达标 OR 已标记完成
          if (m == null || !"DAILY".equalsIgnoreCase(m.getType())) return false;
          LocalDate todayCycle = LocalDate.now();
          if (um.getCycleDate() != null && um.getCycleDate().equals(todayCycle)) {
            return (um.getCompleted() != null && um.getCompleted() == 1)
                || (um.getProgress() != null && m.getTarget() != null && um.getProgress() >= m.getTarget());
          }
          return false;
        })
        .count();

    List<MissionEntity> allDaily = missionMapper.selectList(
        new LambdaQueryWrapper<MissionEntity>()
            .eq(MissionEntity::getActive, 1)
            .eq(MissionEntity::getType, "DAILY"));
    long dailyTotal = allDaily.size();

    // 连续签到天数：统一由 MemberService 计算（从今天往回数，今天未签为 0），避免多处实现口径不一致
    int streak = memberService.getCurrentCheckinStreak(userId);

    Map<String, Object> stats = new HashMap<>();
    stats.put("todayPoints", todayPoints);
    stats.put("dailyDone", dailyDone);
    stats.put("dailyTotal", dailyTotal);
    stats.put("streak", streak);
    return stats;
  }

  /**
   * 用户首次查询任务时为每个任务创建一条 user_mission 记录（progress=0, completed=0, claimed=0）。
   * <p>
   * 注意：本方法会被任务中心页面并发调用（onLoad / onShow 各请求一次），
   * 且外层 listGroupedMissions 未开启事务，因此这里用 insertUserMissionSafely 逐条兜底，
   * 避免并发初始化时唯一索引抛 DuplicateKeyException 导致接口 500。
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
      insertUserMissionSafely(um);
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

    // 周期判定: 若 um 的 cycle_date 与当前周期不符(过期/历史数据),按"新周期"渲染进度 0
    boolean cycleExpired = false;
    if (um != null && um.getCycleDate() != null) {
      LocalDate nowCycle = currentCycleDate(m.getType());
      if (!nowCycle.equals(um.getCycleDate())) {
        cycleExpired = true;
      }
    } else if (um != null && um.getCycleDate() == null && um.getProgress() != null && um.getProgress() > 0) {
      // 历史数据无 cycle_date 但有进度 → 视为上一周期过期,清零显示
      cycleExpired = true;
    }

    int progress = (um == null || um.getProgress() == null || cycleExpired) ? 0 : um.getProgress();
    int completed = (um == null || um.getCompleted() == null || cycleExpired) ? 0 : um.getCompleted();
    // 周期过期的 DAILY/WEEKLY 任务必须一并把 claimed 归零:
    // 否则新周期里仍显示"已领取"且按钮不可点,用户不会再去完成动作,导致本周/今日奖励永远领不到
    // (incrementProgress 在新周期触发时也会把 DB 里的 claimed 置 0,这里只是让展示与之一致)
    // 成就任务周期固定(见 ACHIEVEMENT_CYCLE),正常不会过期;此处的排除仅针对历史脏 cycle_date 兜底
    boolean resetClaimed = cycleExpired && !"ACHIEVEMENT".equalsIgnoreCase(m.getType());
    int claimed = (um == null || um.getClaimed() == null || resetClaimed) ? 0 : um.getClaimed();
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
    // 记录宠物体重属于 Pet Hub 互动,跳转到宠物 Tab 的体重记录入口
    if (n.contains("体重")) return "PET_HUB_INTERACT";
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