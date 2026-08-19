package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.mapper.FeedbackMapper;
import com.moyuyo.service.admin.AdminComplaintService;
import static com.moyuyo.common.enums.GeneralStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, PENDING.name()));
    Long processing = feedbackMapper.selectCount(
        new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getStatus, PROCESSING.name()));
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
  @Transactional
  public void handle(Long id, String result, String note) {
    FeedbackEntity entity = feedbackMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(result);
      entity.setReplyContent(note);
      feedbackMapper.updateById(entity);
    }
  }

  @Override
  @Transactional
  public void assignHandler(Long id, String assignee, String remark) {
    FeedbackEntity entity = feedbackMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("投诉不存在");
    }
    // 同步更新主表：状态由 PENDING 升为 PROCESSING；备注写入 reply_content
    String oldReply = entity.getReplyContent();
    String newReply = (remark == null || remark.isEmpty())
        ? "【已分配给 " + assignee + "】"
        : "【已分配给 " + assignee + "】" + remark;
    entity.setReplyContent(oldReply == null || oldReply.isEmpty() ? newReply : oldReply + "\n" + newReply);
    if ("PENDING".equals(entity.getStatus())) {
      entity.setStatus("PROCESSING");
    }
    feedbackMapper.updateById(entity);
  }
}
