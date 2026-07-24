package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.GroupBuyEntity;
import com.moyuyo.dao.entity.GroupBuyMemberEntity;
import com.moyuyo.dao.mapper.GroupBuyMapper;
import com.moyuyo.dao.mapper.GroupBuyMemberMapper;
import com.moyuyo.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyServiceImpl implements GroupBuyService {

  private final GroupBuyMapper groupBuyMapper;
  private final GroupBuyMemberMapper groupBuyMemberMapper;

  @Override
  public List<GroupBuyEntity> list() {
    return groupBuyMapper.selectList(
        new LambdaQueryWrapper<GroupBuyEntity>()
            .eq(GroupBuyEntity::getStatus, "ACTIVE")
            .orderByDesc(GroupBuyEntity::getCreateTime));
  }

  @Override
  public GroupBuyEntity getDetail(Long id) {
    GroupBuyEntity entity = groupBuyMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("拼团活动不存在");
    }
    return entity;
  }

  @Override
  @Transactional
  public GroupBuyMemberEntity join(Long id, Long userId) {
    GroupBuyEntity groupBuy = groupBuyMapper.selectById(id);
    if (groupBuy == null || !"ACTIVE".equals(groupBuy.getStatus())) {
      throw new IllegalArgumentException("拼团活动不存在或已结束");
    }
    long joined = groupBuyMemberMapper.selectCount(
        new LambdaQueryWrapper<GroupBuyMemberEntity>()
            .eq(GroupBuyMemberEntity::getGroupBuyId, id)
            .eq(GroupBuyMemberEntity::getUserId, userId));
    if (joined > 0) {
      throw new IllegalArgumentException("您已参与该拼团活动");
    }
    GroupBuyMemberEntity member = new GroupBuyMemberEntity();
    member.setGroupBuyId(id);
    member.setUserId(userId);
    member.setIsLeader(groupBuy.getCurrentMembers() == 0);
    groupBuyMemberMapper.insert(member);

    // 使用原子更新防止并发超员：WHERE current_members < min_members
    LambdaUpdateWrapper<GroupBuyEntity> updateWrapper = new LambdaUpdateWrapper<>();
    updateWrapper.eq(GroupBuyEntity::getId, id)
        .setSql("current_members = COALESCE(current_members, 0) + 1")
        .apply("COALESCE(current_members, 0) < min_members");
    int rows = groupBuyMapper.update(null, updateWrapper);
    if (rows == 0) {
      throw new IllegalStateException("拼团人数已满，参与失败");
    }

    log.info("GroupBuy join: groupBuyId={}, userId={}", id, userId);
    return member;
  }

  @Override
  public List<GroupBuyMemberEntity> getMembers(Long id) {
    return groupBuyMemberMapper.selectList(
        new LambdaQueryWrapper<GroupBuyMemberEntity>()
            .eq(GroupBuyMemberEntity::getGroupBuyId, id)
            .orderByDesc(GroupBuyMemberEntity::getCreateTime));
  }
}
