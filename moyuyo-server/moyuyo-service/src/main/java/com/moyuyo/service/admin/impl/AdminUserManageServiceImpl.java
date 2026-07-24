package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.MemberMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminUserManageService;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台用户管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManageServiceImpl implements AdminUserManageService {

  private final UserMapper userMapper;
  private final MemberMapper memberMapper;

  @Override
  public Map<String, Object> getStats() {
    LocalDate today = LocalDate.now();

    // 总用户数
    Long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<>());

    // 今日新增用户（注册时间在今天）
    Long newToday = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getCreatedAt, today)
    );

    // 活跃用户（今日有登录记录）
    Long activeToday = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getLastLoginTime, today)
    );

    // 总会员数
    Long totalMembers = memberMapper.selectCount(new LambdaQueryWrapper<>());

    // 今日新增会员
    Long newMembersToday = memberMapper.selectCount(
        new LambdaQueryWrapper<MemberEntity>()
            .ge(MemberEntity::getCreateTime, today)
    );

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalUsers", totalUsers);
    result.put("newToday", newToday);
    result.put("activeToday", activeToday);
    result.put("totalMembers", totalMembers);
    result.put("newMembersToday", newMembersToday);
    return result;
  }

  @Override
  public Map<String, Object> listUsers(int page, int size, String search, String level, String status) {
    // 构建查询条件
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

    // 关键词搜索（昵称、邮箱）
    if (search != null && !search.isEmpty()) {
      wrapper.and(w -> w
          .like(UserEntity::getNickname, search)
          .or()
          .like(UserEntity::getEmail, search)
      );
    }

    // 按状态筛选
    if (status != null && !status.isEmpty()) {
      wrapper.eq(UserEntity::getStatus, ACTIVE.name().equals(status) ? 1 : 0);
    }

    // 按创建时间倒序
    wrapper.orderByDesc(UserEntity::getCreatedAt);

    // 执行分页查询
    Page<UserEntity> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);

    // 收集所有用户ID，批量查询会员信息
    List<Long> userIds = userPage.getRecords().stream()
        .map(UserEntity::getId)
        .collect(Collectors.toList());

    // 查询会员信息（按user_id分组）
    Map<Long, MemberEntity> memberMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      List<MemberEntity> members = memberMapper.selectList(
          new LambdaQueryWrapper<MemberEntity>()
              .in(MemberEntity::getUserId, userIds)
      );
      for (MemberEntity m : members) {
        memberMap.put(m.getUserId(), m);
      }
    }

    // 组装返回数据
    List<Map<String, Object>> list = new ArrayList<>();
    for (UserEntity user : userPage.getRecords()) {
      MemberEntity member = memberMap.get(user.getId());

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", user.getId());
      item.put("nickname", user.getNickname());
      item.put("email", user.getEmail() != null ? user.getEmail() : "");
      item.put("phone", user.getPhone() != null ? user.getPhone() : "");
      item.put("level", member != null && member.getLevel() != null ? member.getLevel().name() : "NORMAL");
      item.put("status", user.getStatus() != null && user.getStatus() == 1 ? ACTIVE.name() : INACTIVE.name());
      item.put("registerTime", user.getCreatedAt() != null ? user.getCreatedAt().toString().replace("T", " ") : "");
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", userPage.getTotal());
    result.put("page", (long) page);
    result.put("size", (long) size);
    return result;
  }

  @Override
  public Map<String, Object> getUserDetail(Long id) {
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", user.getId());
    detail.put("nickname", user.getNickname());
    detail.put("email", user.getEmail() != null ? user.getEmail() : "");
    detail.put("phone", user.getPhone() != null ? user.getPhone() : "");
    detail.put("status", user.getStatus() != null && user.getStatus() == 1 ? ACTIVE.name() : INACTIVE.name());
    detail.put("registerTime", user.getCreatedAt() != null ? user.getCreatedAt().toString().replace("T", " ") : "");
    detail.put("lastLoginTime", user.getLastLoginTime() != null ? user.getLastLoginTime().toString().replace("T", " ") : "");

    // 查询会员信息
    MemberEntity member = memberMapper.selectOne(
        new LambdaQueryWrapper<MemberEntity>().eq(MemberEntity::getUserId, id));
    if (member != null) {
      detail.put("level", member.getLevel() != null ? member.getLevel().name() : "NORMAL");
      detail.put("points", user.getPoints() != null ? user.getPoints() : 0);
    } else {
      detail.put("level", "NORMAL");
      detail.put("points", 0);
    }
    return detail;
  }

  @Override
  @Transactional
  public void updateUserStatus(Long id, String status) {
    if (id == null) {
      throw new IllegalArgumentException("用户ID不能为空");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("状态参数不能为空");
    }
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    user.setStatus(ACTIVE.name().equals(status) ? 1 : 0);
    userMapper.updateById(user);
  }
}
