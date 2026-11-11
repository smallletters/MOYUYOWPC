package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.service.admin.AdminCsSessionService;
import com.moyuyo.service.admin.CsMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 客服会话管理")
@RestController
@RequestMapping("/api/admin/cs-sessions")
@RequiredArgsConstructor
public class AdminCsSessionController {

    private final AdminCsSessionService adminCsSessionService;
    private final CsMessageService csMessageService;

    @Operation(summary = "会话列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sessionId,
        @RequestParam(required = false) String userId) {
        return Result.success(adminCsSessionService.listAll(page, size, status, sessionId, userId));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(adminCsSessionService.getById(id));
    }

    @Operation(summary = "客服绩效统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(adminCsSessionService.getStats());
    }

    @Operation(summary = "会话用户的最近订单（客服侧栏展示 / 发送订单卡片）")
    @GetMapping("/{id}/user-orders")
    public Result<List<Map<String, Object>>> userOrders(
        @PathVariable Long id,
        @RequestParam(defaultValue = "10") int limit) {
        return Result.success(adminCsSessionService.listUserOrders(id, limit));
    }

    @Operation(summary = "某用户的历史会话列表（客服聊天侧栏切换查看）")
    @GetMapping("/user/{userId}/sessions")
    public Result<List<Map<String, Object>>> userSessions(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "20") int limit) {
        return Result.success(adminCsSessionService.listByUserId(userId, limit));
    }

    // ========== 客服在线聊天相关接口 ==========

    @Operation(summary = "会话的全部消息（按时间升序）")
    @GetMapping("/{id}/messages")
    public Result<List<CsMessageEntity>> messages(@PathVariable Long id) {
        return Result.success(csMessageService.listMessages(id));
    }

    @Operation(summary = "会话消息增量轮询：拉取 since 之后的新消息")
    @GetMapping("/{id}/messages/poll")
    public Result<List<CsMessageEntity>> poll(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return Result.success(csMessageService.pollMessages(id, since));
    }

    @Operation(summary = "把某会话所有 USER 消息标记为已读")
    @PostMapping("/{id}/messages/read")
    public Result<Map<String, Object>> markRead(@PathVariable Long id) {
        int n = csMessageService.markRead(id);
        return Result.success(Map.of("marked", n));
    }

    @Operation(summary = "客服发送一条消息（接管到当前操作员名下）")
    @PostMapping(value = "/{id}/messages", consumes = "application/json;charset=UTF-8", produces = "application/json;charset=UTF-8")
    public Result<CsMessageEntity> sendByAgent(
        @PathVariable Long id,
        @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        // 接收可选 contentType，默认 TEXT；PRODUCT/ORDER 为商品/订单卡片消息
        String contentType = (String) body.getOrDefault("contentType", "TEXT");
        boolean isCard = "PRODUCT".equals(contentType) || "ORDER".equals(contentType);
        if ((content == null || content.isBlank()) && !isCard) {
            return Result.error(400, "消息内容不能为空");
        }
        // 卡片消息引用对象 ID：PRODUCT 时为商品 ID，ORDER 时为订单 ID
        Long refId = null;
        Object refIdObj = body.get("refId");
        if (refIdObj != null && !refIdObj.toString().isBlank()) {
            try {
                refId = Long.valueOf(refIdObj.toString().trim());
            } catch (NumberFormatException e) {
                return Result.error(400, "refId 格式无效");
            }
        }
        if (isCard && refId == null) {
            return Result.error(400, "卡片消息缺少 refId");
        }

        CsMessageEntity m = new CsMessageEntity();
        m.setSessionId(id);
        m.setSenderType("AGENT");
        m.setRefId(refId);
        // 当前操作员：JWT 中的 userId 优先；若过滤器未填充则从前端 body 中取（前端从 /me 拿到后回填）
        Long operatorId = UserContextHolder.getUserId();
        if (operatorId == null && body.get("senderId") != null) {
            operatorId = Long.valueOf(body.get("senderId").toString());
        }
        m.setSenderId(operatorId);
        // 显示名：优先 body 里的 senderName；否则从 userId 推断
        String senderName = (String) body.get("senderName");
        if (senderName == null || senderName.isBlank()) {
            senderName = operatorId == null ? "客服" : ("客服" + operatorId);
        }
        m.setSenderName(senderName);
        m.setContent(content);
        m.setContentType(contentType);
        try {
            return Result.success(csMessageService.sendMessage(m));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 状态机校验失败：会话已关闭、状态非法；或商品/订单卡片引用无效
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "关闭会话")
    @PostMapping("/{id}/close")
    public Result<CsSessionEntity> close(@PathVariable Long id) {
        try {
            return Result.success(csMessageService.closeSession(id));
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "转接会话给其他客服")
    @PostMapping("/{id}/transfer")
    public Result<CsSessionEntity> transfer(
        @PathVariable Long id,
        @RequestBody @Size(min = 1) Map<String, Object> body) {
        Long newOp = body.get("operatorId") == null ? null : Long.valueOf(body.get("operatorId").toString());
        if (newOp == null) return Result.error(400, "缺少 operatorId");
        String operatorName = (String) body.getOrDefault("operatorName", "");
        try {
            return Result.success(csMessageService.transferSession(id, newOp, operatorName));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 乐观锁冲突 / 会话已关闭 / 参数非法
            return Result.error(400, e.getMessage());
        }
    }
}
