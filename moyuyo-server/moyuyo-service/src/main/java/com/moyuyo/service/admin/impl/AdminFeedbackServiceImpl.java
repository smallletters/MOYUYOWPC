package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.mapper.FeedbackMapper;
import com.moyuyo.service.admin.AdminFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

  private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "PROCESSING", "RESOLVED", "CLOSED");

  private final FeedbackMapper feedbackMapper;

  @Override
  public IPage<FeedbackEntity> listFeedback(String status, String type, String keyword, int page, int size) {
    LambdaQueryWrapper<FeedbackEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(FeedbackEntity::getStatus, status.trim().toUpperCase());
    }
    if (type != null && !type.isBlank()) {
      wrapper.eq(FeedbackEntity::getType, type.trim().toUpperCase());
    }
    if (keyword != null && !keyword.isBlank()) {
      String kw = keyword.trim();
      // 内容 / 联系方式 / 用户 ID 任一命中即可，避免运营要在两个搜索框之间来回切
      wrapper.and(w -> w.like(FeedbackEntity::getContent, kw)
          .or().like(FeedbackEntity::getContact, kw)
          .or().eq(FeedbackEntity::getUserId, kw.matches("\\d+") ? Long.valueOf(kw) : -1L));
    }
    wrapper.orderByDesc(FeedbackEntity::getCreateTime);
    return feedbackMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public FeedbackEntity getDetail(Long id) {
    FeedbackEntity entity = id == null ? null : feedbackMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("反馈不存在");
    }
    return entity;
  }

  @Override
  @Transactional
  public void handle(Long id, String status, String replyContent) {
    FeedbackEntity entity = getDetail(id);

    boolean hasReply = replyContent != null && !replyContent.isBlank();
    if (hasReply) {
      entity.setReplyContent(replyContent.trim());
      entity.setReplyTime(LocalDateTime.now());
      // 有回复内容但没指定状态时，默认视为已处理完成
      if (status == null || status.isBlank()) {
        entity.setStatus("RESOLVED");
      }
    }

    if (status != null && !status.isBlank()) {
      String normalized = status.trim().toUpperCase();
      if (!ALLOWED_STATUS.contains(normalized)) {
        throw new IllegalArgumentException("状态不合法: " + status);
      }
      entity.setStatus(normalized);
    }

    if (!hasReply && (status == null || status.isBlank())) {
      throw new IllegalArgumentException("没有需要更新的内容");
    }

    feedbackMapper.updateById(entity);
    log.info("Feedback handled: id={}, status={}, replied={}", id, entity.getStatus(), hasReply);
  }

  @Override
  public Map<String, Object> stats() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", feedbackMapper.selectCount(new LambdaQueryWrapper<>()));
    result.put("pending", countByStatus("PENDING"));
    result.put("processing", countByStatus("PROCESSING"));
    result.put("resolved", countByStatus("RESOLVED"));
    return result;
  }

  private Long countByStatus(String status) {
    return feedbackMapper.selectCount(
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, status));
  }
}
