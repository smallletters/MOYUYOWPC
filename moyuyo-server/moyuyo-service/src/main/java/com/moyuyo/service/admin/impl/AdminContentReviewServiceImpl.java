package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.ContentReviewEntity;
import com.moyuyo.dao.admin.mapper.ContentReviewMapper;
import com.moyuyo.service.admin.AdminContentReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容审核服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminContentReviewServiceImpl implements AdminContentReviewService {

  private final ContentReviewMapper contentReviewMapper;

  @Override
  public Map<String, Object> listAll(int page, int size, String contentType, String status) {
    LambdaQueryWrapper<ContentReviewEntity> wrapper = new LambdaQueryWrapper<>();
    if (contentType != null && !contentType.isEmpty()) {
      wrapper.eq(ContentReviewEntity::getContentType, contentType);
    }
    if (status != null && !status.isEmpty()) {
      wrapper.eq(ContentReviewEntity::getStatus, status);
    }
    wrapper.orderByDesc(ContentReviewEntity::getCreateTime);

    Page<ContentReviewEntity> pageObj = contentReviewMapper.selectPage(new Page<>(page, size), wrapper);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", pageObj.getRecords());
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("contentType", entity.getContentType());
    result.put("contentId", entity.getContentId());
    result.put("userId", entity.getUserId());
    result.put("contentExcerpt", entity.getContentExcerpt());
    result.put("images", entity.getImages());
    result.put("reason", entity.getReason());
    result.put("status", entity.getStatus());
    result.put("reviewerId", entity.getReviewerId());
    result.put("reviewComment", entity.getReviewComment());
    result.put("reviewTime", entity.getReviewTime());
    result.put("autoFlag", entity.getAutoFlag());
    result.put("autoScore", entity.getAutoScore());
    result.put("createTime", entity.getCreateTime());
    return result;
  }

  @Override
  public void approve(Long id, Long reviewerId) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("APPROVED");
      entity.setReviewerId(reviewerId);
      entity.setReviewTime(LocalDateTime.now());
      contentReviewMapper.updateById(entity);
    }
  }

  @Override
  public void reject(Long id, Long reviewerId, String reason, String comment) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("REJECTED");
      entity.setReviewerId(reviewerId);
      entity.setReason(reason);
      entity.setReviewComment(comment);
      entity.setReviewTime(LocalDateTime.now());
      contentReviewMapper.updateById(entity);
    }
  }

  @Override
  public void hide(Long id) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("HIDDEN");
      contentReviewMapper.updateById(entity);
    }
  }

  @Override
  public void deleteContent(Long id) {
    contentReviewMapper.deleteById(id);
  }

  @Override
  public void ban(Long id) {
    ContentReviewEntity entity = contentReviewMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("BANNED");
      contentReviewMapper.updateById(entity);
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

    stats.put("pendingCount", pendingCount);
    stats.put("todayReviewed", todayReviewed);
    stats.put("slaRemaining", "4h");
    stats.put("reviewMode", "机审+人审");
    return stats;
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
