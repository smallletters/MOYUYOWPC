package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.DmMessageEntity;
import com.moyuyo.service.DmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户间私信（Douyin 式私信）
 *
 * 所有接口都要求登录：JwtAuthFilter 默认拦截 /api/v1/messages/*。
 */
@Tag(name = "私信")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

  private final DmService dmService;

  @Operation(summary = "获取或创建与目标用户的会话")
  @PostMapping("/conversations")
  public Result<Map<String, Object>> ensureConversation(@RequestBody(required = false) Map<String, Object> body) {
    Long targetUserId = parseUserId(body == null ? null : body.get("targetUserId"));
    return Result.success(dmService.ensureConversation(UserContextHolder.getUserId(), targetUserId));
  }

  @Operation(summary = "我的私信会话列表")
  @GetMapping("/conversations")
  public Result<Page<Map<String, Object>>> listConversations(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(dmService.listConversations(UserContextHolder.getUserId(), page, clampSize(size)));
  }

  @Operation(summary = "会话消息（打开会话时自动标记已读）")
  @GetMapping("/conversations/{id}/messages")
  public Result<Page<DmMessageEntity>> listMessages(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(dmService.listMessages(UserContextHolder.getUserId(), id, page, clampSize(size)));
  }

  @Operation(summary = "会话内搜索消息（仅匹配未撤回的文本消息）")
  @GetMapping("/conversations/{id}/messages/search")
  public Result<Page<DmMessageEntity>> searchMessages(
      @PathVariable Long id,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(
        dmService.searchMessages(UserContextHolder.getUserId(), id, keyword, page, clampSize(size)));
  }

  @Operation(summary = "发送私信（TEXT 文本 / IMAGE 图片 / VOICE 语音 / FILE 文件，可带 quoteMessageId 引用回复）")
  @PostMapping("/conversations/{id}/messages")
  public Result<DmMessageEntity> sendMessage(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    String content = body == null || body.get("content") == null
        ? "" : String.valueOf(body.get("content"));
    String contentType = body == null || body.get("contentType") == null
        ? "TEXT" : String.valueOf(body.get("contentType"));
    Long quoteMessageId = body == null ? null : parseLong(body.get("quoteMessageId"));
    // 媒体元信息：VOICE 需要时长，FILE 需要文件名与大小（缺失由 DmService 统一拒绝）
    Integer mediaDuration = body == null ? null : parseInteger(body.get("mediaDuration"));
    String mediaName = body == null || body.get("mediaName") == null
        ? null : String.valueOf(body.get("mediaName"));
    Long mediaSize = body == null ? null : parseLong(body.get("mediaSize"));
    return Result.success(dmService.sendMessage(
        UserContextHolder.getUserId(), id, content, contentType, quoteMessageId,
        mediaDuration, mediaName, mediaSize));
  }

  @Operation(summary = "批量删除消息（仅对自己隐藏，不影响对方）")
  @PostMapping("/conversations/{id}/messages/batch-delete")
  public Result<Integer> deleteMessages(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    List<Long> messageIds = new ArrayList<>();
    Object raw = body == null ? null : body.get("messageIds");
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        Long parsed = parseLong(item);
        if (parsed != null) messageIds.add(parsed);
      }
    }
    return Result.success(dmService.deleteMessages(
        UserContextHolder.getUserId(), id, messageIds));
  }

  @Operation(summary = "举报私信消息/用户（进入管理端内容审核队列）")
  @PostMapping("/report")
  public Result<Void> report(@RequestBody(required = false) Map<String, Object> body) {
    Long targetUserId = body == null ? null : parseLong(body.get("targetUserId"));
    Long messageId = body == null ? null : parseLong(body.get("messageId"));
    String reason = body == null || body.get("reason") == null
        ? "" : String.valueOf(body.get("reason"));
    String description = body == null || body.get("description") == null
        ? "" : String.valueOf(body.get("description"));
    dmService.report(UserContextHolder.getUserId(), targetUserId, messageId, reason, description);
    return Result.success();
  }

  @Operation(summary = "撤回消息（仅自己发送的 2 分钟内消息）")
  @PostMapping("/conversations/{conversationId}/messages/{messageId}/recall")
  public Result<DmMessageEntity> recallMessage(
      @PathVariable Long conversationId, @PathVariable Long messageId) {
    return Result.success(
        dmService.recallMessage(UserContextHolder.getUserId(), conversationId, messageId));
  }

  @Operation(summary = "置顶/取消置顶会话（仅影响自己的会话列表）")
  @PostMapping("/conversations/{id}/pin")
  public Result<Map<String, Object>> pinConversation(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    // 缺省视为置顶；显式传 pinned=false 则取消置顶
    boolean pinned = body == null || body.get("pinned") == null
        || Boolean.parseBoolean(String.valueOf(body.get("pinned")));
    return Result.success(dmService.pinConversation(UserContextHolder.getUserId(), id, pinned));
  }

  @Operation(summary = "删除会话（仅对自己隐藏，收到新消息后自动恢复）")
  @DeleteMapping("/conversations/{id}")
  public Result<Void> deleteConversation(@PathVariable Long id) {
    dmService.deleteConversation(UserContextHolder.getUserId(), id);
    return Result.success();
  }

  @Operation(summary = "标记会话已读")
  @PostMapping("/conversations/{id}/read")
  public Result<Void> markRead(@PathVariable Long id) {
    dmService.markRead(UserContextHolder.getUserId(), id);
    return Result.success();
  }

  @Operation(summary = "创建群聊")
  @PostMapping("/groups")
  public Result<Map<String, Object>> createGroup(@RequestBody(required = false) Map<String, Object> body) {
    String name = body == null || body.get("name") == null ? "" : String.valueOf(body.get("name"));
    List<Long> memberIds = parseLongList(body == null ? null : body.get("memberIds"));
    return Result.success(dmService.createGroup(
        UserContextHolder.getUserId(), name, memberIds));
  }

  @Operation(summary = "群详情（群名/群主/成员数）")
  @GetMapping("/groups/{id}")
  public Result<Map<String, Object>> groupDetail(@PathVariable Long id) {
    return Result.success(dmService.groupDetail(UserContextHolder.getUserId(), id));
  }

  @Operation(summary = "修改群设置（群名/群公告/群头像，仅群主）")
  @PutMapping("/groups/{id}")
  public Result<Map<String, Object>> updateGroup(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    // null 表示不修改；空字符串表示清空公告/头像
    String name = body == null || body.get("name") == null
        ? null : String.valueOf(body.get("name"));
    String announcement = body == null || body.get("announcement") == null
        ? null : String.valueOf(body.get("announcement"));
    String avatar = body == null || body.get("avatar") == null
        ? null : String.valueOf(body.get("avatar"));
    return Result.success(dmService.updateGroup(
        UserContextHolder.getUserId(), id, name, announcement, avatar));
  }

  @Operation(summary = "群成员列表")
  @GetMapping("/groups/{id}/members")
  public Result<List<Map<String, Object>>> groupMembers(@PathVariable Long id) {
    return Result.success(dmService.listGroupMembers(UserContextHolder.getUserId(), id));
  }

  @Operation(summary = "邀请群成员（仅群主）")
  @PostMapping("/groups/{id}/members")
  public Result<Integer> addGroupMembers(
      @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    List<Long> memberIds = parseLongList(body == null ? null : body.get("memberIds"));
    return Result.success(dmService.addGroupMembers(
        UserContextHolder.getUserId(), id, memberIds));
  }

  @Operation(summary = "移除群成员 / 退出群聊")
  @DeleteMapping("/groups/{id}/members/{userId}")
  public Result<Void> removeGroupMember(@PathVariable Long id, @PathVariable Long userId) {
    dmService.removeGroupMember(UserContextHolder.getUserId(), id, userId);
    return Result.success();
  }

  @Operation(summary = "私信总未读数")
  @GetMapping("/unread-count")
  public Result<Long> unreadCount() {
    return Result.success(dmService.countUnread(UserContextHolder.getUserId()));
  }

  /** 分页大小限制：默认 20，最大 50，防止一次拉取过多消息 */
  private int clampSize(int size) {
    if (size < 1) return 20;
    return Math.min(size, 50);
  }

  /** 兼容 targetUserId 传数字或字符串（雪花 ID 前端可能以字符串传递） */
  private Long parseUserId(Object raw) {
    if (raw == null) throw new IllegalArgumentException("目标用户无效");
    try {
      if (raw instanceof Number number) return number.longValue();
      return Long.valueOf(String.valueOf(raw).trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("目标用户无效");
    }
  }

  /** 兼容字符串/数字的 Long 解析：无效返回 null（用于可选参数） */
  private Long parseLong(Object raw) {
    if (raw == null) return null;
    try {
      if (raw instanceof Number number) return number.longValue();
      String text = String.valueOf(raw).trim();
      return text.isEmpty() ? null : Long.valueOf(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 兼容字符串/数字的 Integer 解析：无效返回 null */
  private Integer parseInteger(Object raw) {
    if (raw == null) return null;
    try {
      if (raw instanceof Number number) return number.intValue();
      String text = String.valueOf(raw).trim();
      return text.isEmpty() ? null : Integer.valueOf(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 解析成员ID列表（兼容字符串雪花 ID） */
  private List<Long> parseLongList(Object raw) {
    List<Long> ids = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        Long parsed = parseLong(item);
        if (parsed != null) ids.add(parsed);
      }
    }
    return ids;
  }
}
