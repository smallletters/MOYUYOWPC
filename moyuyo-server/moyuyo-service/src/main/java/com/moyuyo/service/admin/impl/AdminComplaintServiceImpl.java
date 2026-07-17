package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.mapper.FeedbackMapper;
import com.moyuyo.service.admin.AdminComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台投诉管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminComplaintServiceImpl implements AdminComplaintService {

  private final FeedbackMapper feedbackMapper;

  @Override
  public Page<FeedbackEntity> listAll(String status, String type, int page, int size) {
    LambdaQueryWrapper<FeedbackEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(FeedbackEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(FeedbackEntity::getType, type);
    }
    wrapper.orderByDesc(FeedbackEntity::getCreateTime);
    return feedbackMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 投诉统计：按类型分组统计
    Long total = feedbackMapper.selectCount(new LambdaQueryWrapper<>());
    Long pending = feedbackMapper.selectCount(
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, "PENDING"));
    Long processing = feedbackMapper.selectCount(
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, "PROCESSING"));
    Long closed = feedbackMapper.selectCount(
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, "CLOSED"));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("pending", pending);
    result.put("processing", processing);
    result.put("closed", closed);
    return result;
  }

  @Override
  public void handle(Long id, String result, String note) {
    FeedbackEntity entity = feedbackMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(result);
      entity.setReplyContent(note);
      feedbackMapper.updateById(entity);
    }
  }
}
