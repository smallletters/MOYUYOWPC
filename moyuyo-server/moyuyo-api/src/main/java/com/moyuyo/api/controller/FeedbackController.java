package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.feedback.FeedbackSubmitRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.XssSanitizer;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.service.FeedbackService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "意见反馈")
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

  private final FeedbackService feedbackService;
  private final ObjectMapper objectMapper;

  @Operation(summary = "提交反馈")
  @PostMapping
  @RateLimiter(name = "feedbackSubmit", fallbackMethod = "submitRateLimitFallback")
  public Result<Void> submit(@Valid @RequestBody FeedbackSubmitRequest request) {
    String images = serializeImages(request.getImages());
    feedbackService.submitFeedback(UserContextHolder.getUserId(),
            request.getType(), request.getContent(), images, request.getContact());
    return Result.success();
  }

  /**
   * 截图列表 → JSON 数组字符串。
   *
   * <p>mo_feedback.images 是 MySQL {@code json} 列，写入非法 JSON 会直接被数据库拒绝
   * （Spring 侧表现为 409「数据冲突」）。历史实现用 {@code String.join(",")} 拼成
   * {@code "/a.jpg,/b.jpg"}，只要带图提交就必然失败 —— 这也是"反馈提交不上"的原因之一。
   */
  private String serializeImages(List<String> images) {
    if (images == null || images.isEmpty()) return null;
    // 只接受 http(s) / 站内相对路径，丢弃 javascript:、data: 等协议（防 XSS）
    List<String> safe = images.stream()
        .map(XssSanitizer::sanitizeUrl)
        .filter(java.util.Objects::nonNull)
        .toList();
    if (safe.isEmpty()) return null;
    try {
      return objectMapper.writeValueAsString(safe);
    } catch (Exception e) {
      log.warn("feedback images serialize failed", e);
      // 截图只是辅助信息，序列化失败不应阻塞反馈提交
      return null;
    }
  }

  /** 反馈限流降级方法 */
  @SuppressWarnings("unused")
  private Result<Void> submitRateLimitFallback(FeedbackSubmitRequest request, RequestNotPermitted e) {
    return Result.error(429, "反馈提交过于频繁，请稍后再试");
  }

  @Operation(summary = "我的反馈列表")
  @GetMapping
  public Result<IPage<FeedbackEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return Result.success(feedbackService.listMyFeedback(UserContextHolder.getUserId(), page, size));
  }
}