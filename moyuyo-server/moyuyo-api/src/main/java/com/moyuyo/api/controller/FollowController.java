package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.community.CommunityPostVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.CommunityPostEntity;
import com.moyuyo.dao.entity.FollowEntity;
import com.moyuyo.dao.mapper.CommunityPostMapper;
import com.moyuyo.dao.mapper.FollowMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.dao.entity.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "关注/好友")
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowMapper followMapper;
  private final CommunityPostMapper postMapper;
  private final UserMapper userMapper;

  @PostMapping
  public Result<Void> follow(@RequestBody Map<String, Long> body) {
    Long userId = UserContextHolder.getUserId();
    Long targetId = body == null ? null : body.get("targetId");
    if (targetId == null || targetId.equals(userId)) throw new IllegalArgumentException("目标用户无效");
    FollowEntity exist = followMapper.selectOne(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, userId)
            .eq(FollowEntity::getTargetId, targetId));
    if (exist != null) return Result.success();
    FollowEntity f = new FollowEntity();
    f.setUserId(userId);
    f.setTargetId(targetId);
    f.setStatus("FOLLOWING");
    followMapper.insert(f);
    return Result.success();
  }

  @DeleteMapping("/{targetId}")
  public Result<Void> unfollow(@PathVariable Long targetId) {
    followMapper.delete(new LambdaQueryWrapper<FollowEntity>()
        .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
        .eq(FollowEntity::getTargetId, targetId));
    return Result.success();
  }

  @GetMapping("/{targetId}/status")
  public Result<Map<String, Boolean>> status(@PathVariable Long targetId) {
    long count = followMapper.selectCount(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
            .eq(FollowEntity::getTargetId, targetId));
    Map<String, Boolean> r = new HashMap<>();
    r.put("following", count > 0);
    return Result.success(r);
  }

  @GetMapping("/following")
  public Result<List<Map<String, Object>>> following() {
    return Result.success(enrichFollowing(followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, UserContextHolder.getUserId())
            .eq(FollowEntity::getStatus, "FOLLOWING")
            .orderByDesc(FollowEntity::getCreateTime))));
  }

  @GetMapping("/followers")
  public Result<List<Map<String, Object>>> followers() {
    return Result.success(enrichFollowers(followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getTargetId, UserContextHolder.getUserId())
            .orderByDesc(FollowEntity::getCreateTime))));
  }

  /**
   * 关注列表（我关注的人）：每条记录附加被关注用户的 id/nickname/avatar。
   */
  private List<Map<String, Object>> enrichFollowing(List<FollowEntity> rows) {
    if (rows.isEmpty()) return Collections.emptyList();
    Set<Long> targetIds = rows.stream().map(FollowEntity::getTargetId).collect(Collectors.toSet());
    Map<Long, UserEntity> userMap = userMapper.selectBatchIds(targetIds).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u));
    List<Map<String, Object>> out = new ArrayList<>(rows.size());
    for (FollowEntity f : rows) {
      Map<String, Object> m = new HashMap<>();
      m.put("followId", f.getId());
      m.put("targetId", f.getTargetId());
      m.put("status", f.getStatus());
      m.put("createdAt", f.getCreateTime());
      UserEntity u = userMap.get(f.getTargetId());
      if (u != null) {
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
      }
      out.add(m);
    }
    return out;
  }

  /** 粉丝列表（关注我的人）：每条记录附加粉丝用户信息 */
  private List<Map<String, Object>> enrichFollowers(List<FollowEntity> rows) {
    if (rows.isEmpty()) return Collections.emptyList();
    Set<Long> userIds = rows.stream().map(FollowEntity::getUserId).collect(Collectors.toSet());
    Map<Long, UserEntity> userMap = userMapper.selectBatchIds(userIds).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u));
    List<Map<String, Object>> out = new ArrayList<>(rows.size());
    for (FollowEntity f : rows) {
      Map<String, Object> m = new HashMap<>();
      m.put("followId", f.getId());
      m.put("userId", f.getUserId());
      m.put("status", f.getStatus());
      m.put("createdAt", f.getCreateTime());
      UserEntity u = userMap.get(f.getUserId());
      if (u != null) {
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
      }
      out.add(m);
    }
    return out;
  }

  /**
   * 「关注 Tab」真实数据源：返回当前用户关注的所有人发布的已发布帖子，按时间倒序分页。
   * 社区「推荐 Tab」若发现 userId 没人关注，会自然返回空集合。
   */
  @GetMapping("/feed")
  public Result<Page<CommunityPostVO>> feed(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    Long me = UserContextHolder.getUserId();
    // 1) 取我关注的所有人 id 列表
    List<FollowEntity> follows = followMapper.selectList(
        new LambdaQueryWrapper<FollowEntity>()
            .eq(FollowEntity::getUserId, me)
            .eq(FollowEntity::getStatus, "FOLLOWING"));
    if (follows.isEmpty()) {
      return Result.success(Page.of(page, size));
    }
    List<Long> targetIds = follows.stream()
        .map(FollowEntity::getTargetId)
        .collect(Collectors.toList());
    // 2) 拉这些人在 mo_community_post 的已发布帖子（status=1），按时间倒序
    Page<CommunityPostEntity> entityPage = postMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<CommunityPostEntity>()
            .eq(CommunityPostEntity::getStatus, 1)
            .in(CommunityPostEntity::getUserId, targetIds)
            .orderByDesc(CommunityPostEntity::getCreateTime));
    return Result.success(toFeedVO(entityPage));
  }

  /** 关注流 VO 转换（复用 CommunityServiceImpl.toVOPage 逻辑会引入循环依赖，这里手写简化版） */
  private Page<CommunityPostVO> toFeedVO(Page<CommunityPostEntity> entityPage) {
    Page<CommunityPostVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    if (entityPage.getRecords().isEmpty()) {
      voPage.setRecords(Collections.emptyList());
      return voPage;
    }
    // 批量拉用户信息
    Set<Long> userIds = entityPage.getRecords().stream().map(CommunityPostEntity::getUserId).collect(Collectors.toSet());
    Map<Long, UserEntity> userMap = userMapper.selectBatchIds(userIds).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u));
    List<CommunityPostVO> vos = entityPage.getRecords().stream().map(p -> {
      CommunityPostVO vo = new CommunityPostVO();
      vo.setId(p.getId());
      vo.setUserId(p.getUserId());
      vo.setContent(p.getContent());
      vo.setImages(null);  // 简化：列表页不展开 images JSON
      vo.setTopic(p.getTopic());
      vo.setLikes(p.getLikes());
      vo.setComments(p.getComments());
      vo.setStatus(p.getStatus());
      vo.setCreateTime(p.getCreateTime());
      UserEntity u = userMap.get(p.getUserId());
      if (u != null) { vo.setUsername(u.getNickname()); vo.setAvatar(u.getAvatar()); }
      return vo;
    }).collect(Collectors.toList());
    voPage.setRecords(vos);
    return voPage;
  }
}
