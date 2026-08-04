package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.utils.XssSanitizer;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.mapper.FeedbackMapper;
import com.moyuyo.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

  private final FeedbackMapper feedbackMapper;

  @Override
  @Transactional
  public void submitFeedback(Long userId, String type, String content, String images, String contact) {
    FeedbackEntity entity = new FeedbackEntity();
    entity.setUserId(userId);
    entity.setType(XssSanitizer.sanitizePlainText(type));
    // 净化反馈内容，防止存储型 XSS
    entity.setContent(XssSanitizer.sanitizeRichText(content));
    entity.setImages(images);
    entity.setContact(XssSanitizer.sanitizePlainText(contact));
    entity.setStatus("PENDING");
    feedbackMapper.insert(entity);
    log.info("Feedback submitted: userId={}, type={}", userId, type);
  }

  @Override
  public IPage<FeedbackEntity> listMyFeedback(Long userId, int page, int size) {
    return feedbackMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<FeedbackEntity>()
            .eq(FeedbackEntity::getUserId, userId)
            .orderByDesc(FeedbackEntity::getCreateTime));
  }
}
