package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.service.CsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "客服系统")
@RestController
@RequestMapping("/api/v1/cs")
@RequiredArgsConstructor
public class CsController {

  private final CsService csService;

  @GetMapping("/sessions")
  public Result<Page<CsSessionEntity>> listSessions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(csService.listUserSessions(UserContextHolder.getUserId(), page, size));
  }

  @PostMapping("/sessions")
  public Result<CsSessionEntity> createSession(@RequestBody(required = false) Map<String, String> body) {
    String category = body == null ? null : body.get("category");
    return Result.success(csService.createSession(UserContextHolder.getUserId(), category));
  }

  @GetMapping("/sessions/{id}/messages")
  public Result<List<CsMessageEntity>> listMessages(@PathVariable Long id) {
    return Result.success(csService.listMessages(id, UserContextHolder.getUserId()));
  }

  @PostMapping("/sessions/{id}/messages")
  public Result<CsMessageEntity> sendMessage(@PathVariable Long id, @RequestBody Map<String, String> body) {
    String content = body == null ? "" : body.getOrDefault("content", "");
    return Result.success(csService.sendUserMessage(UserContextHolder.getUserId(), id, content));
  }

  @PostMapping("/sessions/{id}/close")
  public Result<Void> closeSession(@PathVariable Long id) {
    csService.closeSession(UserContextHolder.getUserId(), id);
    return Result.success();
  }

  @GetMapping("/unread-count")
  public Result<Long> unreadCount() {
    return Result.success(csService.countUnread(UserContextHolder.getUserId()));
  }
}
