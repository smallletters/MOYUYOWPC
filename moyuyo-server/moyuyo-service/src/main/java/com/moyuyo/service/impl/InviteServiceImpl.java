package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.InviteEntity;
import com.moyuyo.dao.mapper.InviteMapper;
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
    // 一次查询所有邀请记录，在内存中计算各项统计
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

  private String generateInviteCode() {
    return "MY" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }
}
