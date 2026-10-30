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

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

  /** 落库允许的反馈类型（与 mo_feedback.type 列注释一致） */
  private static final Set<String> ALLOWED_TYPES = Set.of("BUG", "SUGGESTION", "COMPLAINT", "OTHER");

  private final FeedbackMapper feedbackMapper;

  @Override
  @Transactional
  public void submitFeedback(Long userId, String type, String content, String images, String contact) {
    FeedbackEntity entity = new FeedbackEntity();
    entity.setUserId(userId);
    entity.setType(normalizeType(type));
    // 反馈正文/联系方式都是纯文本，最终由 App 的 <text> 与管理端 {{ }} 渲染，
    // 必须用 sanitizeText（不做 HTML 实体转义）—— 用 sanitizeRichText/PlainText
    // 会把 "，" 存成 "&#xff0c;"、"@" 存成 "&#64;"，两端都显示成乱码。
    entity.setContent(XssSanitizer.sanitizeText(content));
    // images 是 JSON 数组字符串（mo_feedback.images 为 json 列），已在 Controller 序列化
    entity.setImages(images);
    entity.setContact(XssSanitizer.sanitizeText(contact));
    entity.setStatus("PENDING");
    feedbackMapper.insert(entity);
    log.info("Feedback submitted: userId={}, type={}", userId, entity.getType());
  }

  @Override
  public IPage<FeedbackEntity> listMyFeedback(Long userId, int page, int size) {
    return feedbackMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<FeedbackEntity>()
            .eq(FeedbackEntity::getUserId, userId)
            .orderByDesc(FeedbackEntity::getCreateTime));
  }

  /**
   * 反馈类型归一化。
   *
   * <p>App 早期版本发送的是 FEATURE / UX / CONTENT / SUGGEST 等别名，
   * 为免老客户端提交直接失败，这里统一映射到落库标准值。
   */
  static String normalizeType(String raw) {
    String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    switch (v) {
      case "FEATURE":
      case "SUGGEST":
      case "UX":
        return "SUGGESTION";
      case "CONTENT":
        return "COMPLAINT";
      default:
        if (ALLOWED_TYPES.contains(v)) return v;
        throw new IllegalArgumentException("反馈类型不合法: " + raw);
    }
  }
}
