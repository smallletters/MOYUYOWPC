// 客服系统 API
import { get, post } from '@/utils/request'

export function listSessions(params = {}) {
  return get('/api/v1/cs/sessions', params)
}

export function createSession(category, context = {}) {
  // context 携带进入客服时的商品/订单上下文，后台客服页可展示商品图与订单
  return post('/api/v1/cs/sessions', { category, ...context })
}

export function listMessages(sessionId) {
  return get(`/api/v1/cs/sessions/${sessionId}/messages`)
}

export function sendMessage(sessionId, content) {
  return post(`/api/v1/cs/sessions/${sessionId}/messages`, { content })
}

export function closeSession(sessionId) {
  return post(`/api/v1/cs/sessions/${sessionId}/close`)
}

export function unreadCount() {
  return get('/api/v1/cs/unread-count')
}

// 消息页用的客服概览：最近会话 + 最后一条消息 + 未读数（不会标记已读）
export function getOverview() {
  return get('/api/v1/cs/overview')
}

export default {
  listSessions,
  createSession,
  listMessages,
  sendMessage,
  closeSession,
  unreadCount,
  getOverview,
}
