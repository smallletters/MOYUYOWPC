package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台 - 意见反馈。
 *
 * <p>数据源与"投诉管理"相同（mo_feedback），但这里的视角是"用户反馈箱"：
 * 投诉（type=COMPLAINT）可继续在投诉管理里走处理流水，
 * 建议/缺陷（SUGGESTION / BUG / OTHER）此前在后台完全没有入口，只能查库。
 */
@Slf4j
@Tag(name = "管理后台 - 意见反馈")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

  private final AdminFeedbackService adminFeedbackService;
  private final UserMapper userMapper;
  private final ObjectMapper objectMapper;

  @Operation(summary = "反馈统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminFeedbackService.stats());
  }

  @Operation(summary = "反馈列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {

    IPage<FeedbackEntity> pageResult = adminFeedbackService.listFeedback(status, type, keyword, page, size);
    Map<Long, UserEntity> userMap = loadUsers(pageResult.getRecords());

    List<Map<String, Object>> list = pageResult.getRecords().stream()
        .map(f -> toItem(f, userMap.get(f.getUserId()), false))
        .collect(Collectors.toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  @Operation(summary = "反馈详情")
  @GetMapping("/{id}")
  public Result<Map<String, Object>> detail(@PathVariable Long id) {
    FeedbackEntity feedback = adminFeedbackService.getDetail(id);
    Map<Long, UserEntity> userMap = loadUsers(List.of(feedback));
    return Result.success(toItem(feedback, userMap.get(feedback.getUserId()), true));
  }

  @Operation(summary = "处理/回复反馈")
  @PutMapping("/{id}")
  @AdminAudit(action = "UPDATE", module = "CONTENT",
      resourceId = "#id", detail = "管理员处理用户反馈")
  public Result<Map<String, Object>> handle(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
    String status = body == null || body.get("status") == null ? null : body.get("status").toString();
    String replyContent = body == null || body.get("replyContent") == null
        ? null : body.get("replyContent").toString();

    adminFeedbackService.handle(id, status, replyContent);

    FeedbackEntity updated = adminFeedbackService.getDetail(id);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", updated.getId());
    result.put("status", updated.getStatus());
    result.put("replyContent", updated.getReplyContent());
    result.put("replyTime", updated.getReplyTime());
    result.put("message", "已处理");
    return Result.success(result);
  }

  /** 批量补充用户昵称/邮箱，避免 N+1 查询 */
  private Map<Long, UserEntity> loadUsers(List<FeedbackEntity> records) {
    Set<Long> ids = records.stream()
        .map(FeedbackEntity::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (ids.isEmpty()) return Map.of();
    return userMapper.selectBatchIds(ids).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));
  }

  private Map<String, Object> toItem(FeedbackEntity f, UserEntity user, boolean withImages) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", f.getId());
    item.put("userId", f.getUserId());
    item.put("nickname", user == null ? null : user.getNickname());
    item.put("email", user == null ? null : user.getEmail());
    item.put("avatar", user == null ? null : user.getAvatar());
    item.put("type", f.getType());
    item.put("content", f.getContent());
    item.put("contact", f.getContact());
    item.put("status", f.getStatus());
    item.put("replyContent", f.getReplyContent());
    item.put("replyTime", f.getReplyTime());
    item.put("createTime", f.getCreateTime());
    if (withImages) {
      item.put("images", parseImages(f.getImages()));
    }
    return item;
  }

  /**
   * images 列是 json 类型，正常存的是 {@code ["/uploads/a.jpg"]}；
   * 早期脏数据可能是逗号拼接的字符串，这里做一次兼容解析，避免详情页 500。
   */
  private List<String> parseImages(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    String trimmed = raw.trim();
    if (trimmed.startsWith("[")) {
      try {
        return objectMapper.readValue(trimmed, objectMapper.getTypeFactory()
            .constructCollectionType(List.class, String.class));
      } catch (Exception e) {
        log.warn("feedback images parse failed: {}", trimmed);
        return List.of();
      }
    }
    return Arrays.stream(trimmed.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
