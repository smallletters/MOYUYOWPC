package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.InviteEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.InviteMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.InviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

  private final InviteMapper inviteMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;

  /** 邀请完成（首单）后双方各得的积分数（与设计稿一致：双方各得 200） */
  private static final int INVITE_REWARD_POINTS = 200;

  @Override
  @Transactional
  public String getInviteCode(Long userId) {
    InviteEntity existing = inviteMapper.selectOne(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId)
            .isNull(InviteEntity::getInvitedUserId));

    if (existing != null) {
      return existing.getInviteCode();
    }

    String code = generateInviteCode();
    InviteEntity entity = new InviteEntity();
    entity.setUserId(userId);
    entity.setInviteCode(code);
    entity.setStatus("PENDING");
    entity.setPointsAwarded(0);
    inviteMapper.insert(entity);

    return code;
  }

  @Override
  public Map<String, Object> getInviteStats(Long userId) {
    List<InviteEntity> records = inviteMapper.selectList(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId));

    long invitedCount = records.stream()
        .filter(r -> r.getInvitedUserId() != null)
        .count();

    int earnedPoints = records.stream()
        .mapToInt(r -> r.getPointsAwarded() != null ? r.getPointsAwarded() : 0)
        .sum();

    long completedOrders = records.stream()
        .filter(r -> "ORDERED".equals(r.getStatus()))
        .count();

    Map<String, Object> stats = new HashMap<>();
    stats.put("invitedCount", invitedCount);
    stats.put("earnedPoints", earnedPoints);
    stats.put("completedOrders", completedOrders);
    return stats;
  }

  @Override
  public IPage<InviteEntity> getInviteHistory(Long userId, int page, int size) {
    return inviteMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId)
            .isNotNull(InviteEntity::getInvitedUserId)
            .orderByDesc(InviteEntity::getCreateTime));
  }

  /**
   * 被邀请人通过邀请码注册时调用。状态从 PENDING → REGISTERED。
   * 此时不发放积分；首单完成后由 OrderService 调用 markOrdered 发放。
   */
  @Override
  @Transactional
  public InviteEntity bindInvitee(String inviteCode, Long inviteeUserId) {
    if (inviteCode == null || inviteCode.isBlank()) {
      return null;
    }
    InviteEntity invite = inviteMapper.selectOne(
        new LambdaQueryWrapper<InviteEntity>().eq(InviteEntity::getInviteCode, inviteCode));
    if (invite == null || invite.getInvitedUserId() != null) {
      return null; // 邀请码不存在或已被使用
    }
    invite.setInvitedUserId(inviteeUserId);
    invite.setStatus("REGISTERED");
    inviteMapper.updateById(invite);
    log.info("Invite bind: inviteCode={} inviteeUserId={}", inviteCode, inviteeUserId);
    return invite;
  }

  /**
   * 被邀请人首单完成后调用，给邀请人+被邀请人各加 200 积分。
   */
  @Override
  @Transactional
  public InviteEntity markOrdered(String inviteCode, Long inviteeUserId) {
    if (inviteCode == null) return null;
    InviteEntity invite = inviteMapper.selectOne(
        new LambdaQueryWrapper<InviteEntity>().eq(InviteEntity::getInviteCode, inviteCode));
    if (invite == null) return null;

    if ("ORDERED".equals(invite.getStatus())) {
      return invite; // 已发放过奖励，避免重复
    }

    // 邀请人获得积分
    awardPoints(invite.getUserId(), INVITE_REWARD_POINTS, "INVITE", String.valueOf(inviteeUserId),
        "邀请好友首单奖励");

    // 被邀请人获得积分
    awardPoints(inviteeUserId, INVITE_REWARD_POINTS, "INVITE", String.valueOf(invite.getUserId()),
        "受邀首单奖励");

    invite.setStatus("ORDERED");
    invite.setPointsAwarded(INVITE_REWARD_POINTS);
    inviteMapper.updateById(invite);

    log.info("Invite ordered: inviteCode={} inviteeUserId={} pointsEach={}",
        inviteCode, inviteeUserId, INVITE_REWARD_POINTS);
    return invite;
  }

  private void awardPoints(Long userId, int changeValue, String type, String bizNo, String remark) {
    if (userId == null) return;
    UserEntity user = userMapper.selectById(userId);
    if (user == null) return;
    int newPoints = (user.getPoints() == null ? 0 : user.getPoints()) + changeValue;
    if (newPoints < 0) return;
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

  private String generateInviteCode() {
    return "MY" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }
}