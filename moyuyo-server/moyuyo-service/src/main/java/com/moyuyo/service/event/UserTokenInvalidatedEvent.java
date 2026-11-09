package com.moyuyo.service.event;

/**
 * 用户 token 被强制失效事件。
 * <p>
 * 改密 / 重置密码后发布：版本号已递增，旧 access token 无法再通过 HTTP 鉴权；
 * api 模块监听该事件后，主动关闭该用户已建立的私信 WebSocket 长连接。
 *
 * @param userId 用户 ID
 * @param reason 失效原因（仅用于日志与客户端提示，如 PASSWORD_CHANGED / PASSWORD_RESET）
 */
public record UserTokenInvalidatedEvent(Long userId, String reason) {
}
