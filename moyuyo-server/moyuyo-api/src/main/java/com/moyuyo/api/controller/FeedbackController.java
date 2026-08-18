package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.feedback.FeedbackSubmitRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.service.FeedbackService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "意见反馈")
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

  private final FeedbackService feedbackService;

  @Operation(summary = "提交反馈")
  @PostMapping
  @RateLimiter(name = "feedbackSubmit", fallbackMethod = "submitRateLimitFallback")
  public Result<Void> submit(@Valid @RequestBody FeedbackSubmitRequest request) {
    // images 列表用逗号拼接为字符串（与 FeedbackService 历史契约一致）；
    // 若未来需要拆开存储，仅需调整此处与 FeedbackService 的入参即可
    String images = request.getImages() == null ? null : String.join(",", request.getImages());
    feedbackService.submitFeedback(UserContextHolder.getUserId(),
            request.getType(), request.getContent(), images, request.getContact());
    return Result.success();
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