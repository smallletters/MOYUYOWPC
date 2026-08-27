package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.annotation.OperationLog;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.dao.admin.entity.ContentReviewEntity;
import com.moyuyo.dao.admin.mapper.ContentReviewMapper;
import com.moyuyo.dao.entity.CommunityPostEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.CommunityPostMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminContentReviewService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内容审核服务实现
 *
 * <p>
 * 修复记录（2026-08-26）：
 * - 列表/详情接口补充 username / avatar（批量 join user 表）
 * - contentType=POST 时通过 contentId 联查 mo_community_post 取完整内容（之前只有 500 字摘要）
 * - images 字段从 String（JSON数组字符串）转为 List<String> 直接返回前端
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AdminContentReviewServiceImpl implements AdminContentReviewService {

  private static final Logger log = LoggerFactory.getLogger(AdminContentReviewServiceImpl.class);

  private final ContentReviewMapper contentReviewMapper;
  private final UserMapper userMapper;
  private final CommunityPostMapper communityPostMapper;

  @Override
  public Map<String, Object> listAll(int page, int size, String contentType, String status, String reasonLike) {
    LambdaQueryWrapper<ContentReviewEntity> wrapper = new LambdaQueryWrapper<>();
    if (contentType != null && !contentType.isEmpty()) {
      wrapper.eq(ContentReviewEntity::getContentType, contentType);
    }
    if (status != null && !status.isEmpty()) {
      wrapper.eq(ContentReviewEntity::getStatus, status);
    }
    // 违规类型筛选(对应前端 tab:色情/暴力/...):reason 字段 LIKE '%违规类型%'
    // 转义 LIKE 元字符避免通配符注入
    if (reasonLike != null && !reasonLike.isBlank()) {
      String safe = reasonLike.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      wrapper.like(ContentReviewEntity::getReason, "%" + safe + "%");
    }
    wrapper.orderByDesc(ContentReviewEntity::getCreateTime);

    Page<ContentReviewEntity> pageObj = contentReviewMapper.selectPage(new Page<>(page, size), wrapper);

    // 1. 批量 join 用户信息
    List<Long> userIds = pageObj.getRecords().stream()
        .map(ContentReviewEntity::getUserId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    Map<Long, UserEntity> userMap = userIds.isEmpty() ? Collections.emptyMap() :
        userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, u -> u));

    // 2. contentType=POST 的记录批量联查帖子实体,补全 content / images 列表
    List<Long> postIds = pageObj.getRecords().stream()
        .filter(e -> "POST".equalsIgnoreCase(e.getContentType()) && e.getContentId() != null)
        .map(ContentReviewEntity::getContentId)
        .distinct()
        .collect(Collectors.toList());
    Map<Long, CommunityPostEntity> postMap = postIds.isEmpty() ? Collections.emptyMap() :
        communityPostMapper.selectBatchIds(postIds).stream()
            .collect(Collectors.toMap(CommunityPostEntity::getId, p -> p));

    // 3. 组装 VO
    List<Map<String, Object>> voList = pageObj.getRecords().stream()
        .map(entity -> toReviewVO(entity, userMap, postMap))
        .collect(Collectors.toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", voList);
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    result.put("mode", "manual");
    return result;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity == null) {
      return new LinkedHashMap<>();
    }

    // 查用户信息
    Map<Long, UserEntity> userMap = Collections.emptyMap();
    if (entity.getUserId() != null) {
      UserEntity user = userMapper.selectById(entity.getUserId());
      if (user != null) {
        userMap = Map.of(user.getId(), user);
      }
    }
    // 查帖子详情(contentType=POST)
    Map<Long, CommunityPostEntity> postMap = Collections.emptyMap();
    if ("POST".equalsIgnoreCase(entity.getContentType()) && entity.getContentId() != null) {
      CommunityPostEntity post = communityPostMapper.selectById(entity.getContentId());
      if (post != null) {
        postMap = Map.of(post.getId(), post);
      }
    }
    return toReviewVO(entity, userMap, postMap);
  }

  /**
   * 把 ContentReviewEntity 转成前端可用的 VO map,补 username/avatar/content/imagesList
   */
  private Map<String, Object> toReviewVO(ContentReviewEntity entity,
                                        Map<Long, UserEntity> userMap,
                                        Map<Long, CommunityPostEntity> postMap) {
    Map<String, Object> vo = new LinkedHashMap<>();
    vo.put("id", entity.getId());
    vo.put("contentType", entity.getContentType());
    vo.put("contentId", entity.getContentId());
    vo.put("userId", entity.getUserId());
    vo.put("contentExcerpt", entity.getContentExcerpt());
    vo.put("reason", entity.getReason());
    vo.put("status", entity.getStatus());
    vo.put("reviewerId", entity.getReviewerId());
    vo.put("reviewComment", entity.getReviewComment());
    vo.put("reviewTime", entity.getReviewTime());
    vo.put("autoFlag", entity.getAutoFlag());
    vo.put("autoScore", entity.getAutoScore());
    vo.put("createTime", entity.getCreateTime());

    // 用户名 / 头像
    UserEntity user = entity.getUserId() == null ? null : userMap.get(entity.getUserId());
    if (user != null) {
      vo.put("username", user.getNickname());
      vo.put("avatar", user.getAvatar());
    } else {
      vo.put("username", null);
      vo.put("avatar", null);
    }

    // 帖子类型时:补完整 content + 图片列表(优先用帖子实体的真实数据)
    String fullContent = entity.getContentExcerpt();
    List<String> imageList = Collections.emptyList();
    if ("POST".equalsIgnoreCase(entity.getContentType()) && entity.getContentId() != null) {
      CommunityPostEntity post = postMap.get(entity.getContentId());
      if (post != null) {
        if (post.getContent() != null) {
          fullContent = post.getContent();
        }
        if (post.getImages() != null && !post.getImages().isBlank()) {
          imageList = JsonUtils.parseStringArray(post.getImages());
        }
        // 帖子属性补充(topic / likes / comments)
        vo.put("topic", post.getTopic());
        vo.put("likes", post.getLikes());
        vo.put("comments", post.getComments());
        vo.put("postStatus", post.getStatus());
        vo.put("postCreateTime", post.getCreateTime());
      }
    } else if (entity.getImages() != null && !entity.getImages().isBlank()) {
      // 其他内容类型(评论/反馈)用 review.images 字段
      imageList = JsonUtils.parseStringArray(entity.getImages());
    }
    vo.put("content", fullContent);
    vo.put("images", imageList);
    // 兼容字段:前端可能仍读 review.images(原始 String)
    vo.put("imagesRaw", entity.getImages());
    return vo;
  }

  private void validateReviewState(ContentReviewEntity entity) {
    if (entity == null) {
      throw new IllegalArgumentException("审核记录不存在");
    }
    if (!"PENDING".equals(entity.getStatus())) {
      throw new IllegalStateException("审核记录已处理");
    }
  }

  @Override
  @OperationLog(type = "内容审核-通过", detail = "#id", logParams = false)
  public void approve(Long id, Long reviewerId) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    validateReviewState(entity);
    entity.setStatus("APPROVED");
    entity.setReviewerId(reviewerId);
    entity.setReviewTime(LocalDateTime.now());
    contentReviewMapper.updateById(entity);
  }

  @Override
  @OperationLog(type = "内容审核-驳回", detail = "#id", logParams = false)
  public void reject(Long id, Long reviewerId, String reason, String comment) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("驳回原因不能为空");
    }
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    validateReviewState(entity);
    entity.setStatus("REJECTED");
    entity.setReviewerId(reviewerId);
    entity.setReason(reason);
    entity.setReviewComment(comment);
    entity.setReviewTime(LocalDateTime.now());
    contentReviewMapper.updateById(entity);
  }

  @Override
  @OperationLog(type = "内容审核-隐藏", detail = "#id", logParams = false)
  public void hide(Long id) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    validateReviewState(entity);
    entity.setStatus("HIDDEN");
    entity.setReviewTime(LocalDateTime.now());
    contentReviewMapper.updateById(entity);
  }

  @Override
  @OperationLog(type = "内容审核-删除", detail = "#id", logParams = false)
  public void deleteContent(Long id) {
    // 删除动作允许在任意状态下执行：已通过/已驳回/已隐藏/已封禁的评论都可被管理员再次删除
    // 仅校验记录存在，避免 PENDING 校验拦截正常管理操作
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("审核记录不存在");
    }
    entity.setStatus("DELETED");
    entity.setReviewTime(LocalDateTime.now());
    contentReviewMapper.updateById(entity);
  }

  @Override
  @OperationLog(type = "内容审核-封禁", detail = "#id", logParams = false)
  public void ban(Long id, Long reviewerId, String banType, String comment) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    validateReviewState(entity);
    entity.setStatus("BANNED");
    entity.setReviewerId(reviewerId);
    // reason 存违规类型(便于统计/审计);若comment 非空则拼在后面
    String reason = banType == null ? "其他违规" : banType;
    if (comment != null && !comment.isBlank()) {
      reason = reason + " | " + comment;
    }
    entity.setReason(reason);
    entity.setReviewComment(comment == null ? "" : comment);
    entity.setReviewTime(LocalDateTime.now());
    contentReviewMapper.updateById(entity);

    // 联动:被 BANNED 的帖子内容必须在 C 端社区列表中隐藏
    // contentType=POST 时通过 contentId 反查并软删帖子
    if ("POST".equalsIgnoreCase(entity.getContentType()) && entity.getContentId() != null) {
      try {
        CommunityPostEntity post = communityPostMapper.selectById(entity.getContentId());
        if (post != null && Integer.valueOf(1).equals(post.getStatus())) {
          post.setStatus(0); // 0 = 隐藏,与社区列表只取 status=1 的查询对齐
          communityPostMapper.updateById(post);
          log.info("[content-review] post banned hidden: postId={}", post.getId());
        }
      } catch (Exception ex) {
        // 联动失败不阻断审核主流程,记录日志便于人工补刀
        log.error("[content-review] ban post联动失败: reviewId={}, postId={}", id, entity.getContentId(), ex);
      }
    }
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();

    // 待审核数量
    LambdaQueryWrapper<ContentReviewEntity> pendingWrapper = new LambdaQueryWrapper<>();
    pendingWrapper.eq(ContentReviewEntity::getStatus, "PENDING");
    long pendingCount = contentReviewMapper.selectCount(pendingWrapper);

    // 今日已审核数量（通过或驳回）
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
    LambdaQueryWrapper<ContentReviewEntity> todayWrapper = new LambdaQueryWrapper<>();
    todayWrapper.in(ContentReviewEntity::getStatus, "APPROVED", "REJECTED")
        .between(ContentReviewEntity::getReviewTime, todayStart, todayEnd);
    long todayReviewed = contentReviewMapper.selectCount(todayWrapper);

    // 计算 SLA 剩余时间：基于最早一条待审核记录的提交时间，默认 SLA 为 4 小时
    String slaRemaining = "4h";
    if (pendingCount > 0) {
      LambdaQueryWrapper<ContentReviewEntity> oldestWrapper = new LambdaQueryWrapper<>();
      oldestWrapper.eq(ContentReviewEntity::getStatus, "PENDING")
          .orderByAsc(ContentReviewEntity::getCreateTime)
          .last("LIMIT 1");
      ContentReviewEntity oldest = contentReviewMapper.selectOne(oldestWrapper);
      if (oldest != null && oldest.getCreateTime() != null) {
        long remainingMinutes = 240 - java.time.Duration.between(oldest.getCreateTime(), LocalDateTime.now()).toMinutes();
        if (remainingMinutes < 0) remainingMinutes = 0;
        slaRemaining = remainingMinutes + "min";
      }
    }

    stats.put("pendingCount", pendingCount);
    stats.put("todayReviewed", todayReviewed);
    stats.put("slaRemaining", slaRemaining);
    stats.put("reviewMode", "机审+人审");
    return stats;
  }

  @Override
  public int seedTestData() {
    // 6 条违规类型各一条,覆盖前端 tab 的 6 个分类
    String[][] seedRows = new String[][] {
      {"色情", "测试 - 色情内容"},
      {"暴力", "测试 - 暴力血腥"},
      {"仇恨言论", "测试 - 仇恨言论"},
      {"侵权", "测试 - 侵权盗版"},
      {"虚假信息", "测试 - 虚假信息"},
      {"虐待动物", "测试 - 虐待动物"},
    };
    int count = 0;
    for (String[] row : seedRows) {
      ContentReviewEntity e = new ContentReviewEntity();
      e.setContentType("POST");
      e.setContentId(0L); // 测试用,不关联真实 post
      e.setUserId(0L);
      e.setContentExcerpt("[" + row[0] + "] " + row[1] + " 演示数据");
      e.setImages("[]");
      e.setReason(row[0]);
      e.setStatus("BANNED");
      e.setReviewerId(1L);
      e.setReviewComment(row[1]);
      e.setReviewTime(LocalDateTime.now());
      e.setAutoFlag(0);
      contentReviewMapper.insert(e);
      count++;
    }
    log.info("[content-review] seedTestData inserted: {}", count);
    return count;
  }

  @Override
  public List<Map<String, Object>> getTrend(int days) {
    List<Map<String, Object>> trend = new ArrayList<>();
    LocalDate today = LocalDate.now();

    for (int i = days - 1; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
      LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

      // 当日通过数
      LambdaQueryWrapper<ContentReviewEntity> passWrapper = new LambdaQueryWrapper<>();
      passWrapper.eq(ContentReviewEntity::getStatus, "APPROVED")
          .between(ContentReviewEntity::getReviewTime, dayStart, dayEnd);
      long passCount = contentReviewMapper.selectCount(passWrapper);

      // 当日驳回数
      LambdaQueryWrapper<ContentReviewEntity> rejectWrapper = new LambdaQueryWrapper<>();
      rejectWrapper.eq(ContentReviewEntity::getStatus, "REJECTED")
          .between(ContentReviewEntity::getReviewTime, dayStart, dayEnd);
      long rejectCount = contentReviewMapper.selectCount(rejectWrapper);

      Map<String, Object> dayItem = new LinkedHashMap<>();
      dayItem.put("date", date.toString());
      dayItem.put("passCount", passCount);
      dayItem.put("rejectCount", rejectCount);
      trend.add(dayItem);
    }
    return trend;
  }
}