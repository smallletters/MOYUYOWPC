// 用户间私信 API（后端 /api/v1/messages）
import { get, post, put, del } from '@/utils/request'

/** 获取或创建与目标用户的会话（幂等） */
export function ensureConversation(targetUserId) {
  return post('/api/v1/messages/conversations', { targetUserId })
}

/** 我的私信会话列表（按最后消息时间倒序） */
export function listConversations(params = {}) {
  return get('/api/v1/messages/conversations', params)
}

/** 会话消息分页（打开会话时后端自动标记已读） */
export function listMessages(conversationId, params = {}) {
  return get(`/api/v1/messages/conversations/${conversationId}/messages`, params)
}

/** 会话内消息搜索（仅匹配未撤回的文本消息） */
export function searchMessages(conversationId, keyword, params = {}) {
  return get(`/api/v1/messages/conversations/${conversationId}/messages/search`, {
    keyword,
    ...params,
  })
}

/**
 * 发送私信（contentType: TEXT 文本 / IMAGE 图片 / VOICE 语音 / FILE 文件）
 * @param {object} [media] 媒体元信息：voice 传 {mediaDuration}，file 传 {mediaName, mediaSize}
 */
export function sendMessage(
  conversationId,
  content,
  contentType = 'TEXT',
  quoteMessageId = null,
  media = null,
) {
  const body = { content, contentType }
  if (quoteMessageId) body.quoteMessageId = quoteMessageId
  if (media) {
    if (media.mediaDuration != null) body.mediaDuration = media.mediaDuration
    if (media.mediaName) body.mediaName = media.mediaName
    if (media.mediaSize != null) body.mediaSize = media.mediaSize
  }
  return post(
    `/api/v1/messages/conversations/${conversationId}/messages`,
    body,
    // 关闭请求层自动 toast，由聊天页按具体错误（对方关闭私信/拉黑等）统一提示
    { showError: false },
  )
}

/** 批量删除消息（仅对自己隐藏，不影响对方） */
export function deleteMessages(conversationId, messageIds) {
  return post(
    `/api/v1/messages/conversations/${conversationId}/messages/batch-delete`,
    { messageIds },
    { showError: false },
  )
}

/** 举报私信消息/用户（进入管理端内容审核队列） */
export function report({ targetUserId, messageId = null, reason, description = '' }) {
  return post(
    '/api/v1/messages/report',
    { targetUserId, messageId, reason, description },
    { showError: false },
  )
}

/** 撤回消息（仅自己发送的 2 分钟内消息） */
export function recallMessage(conversationId, messageId) {
  return post(
    `/api/v1/messages/conversations/${conversationId}/messages/${messageId}/recall`,
    {},
    { showError: false },
  )
}

/** 标记会话已读 */
export function markRead(conversationId) {
  return post(`/api/v1/messages/conversations/${conversationId}/read`)
}

/** 置顶/取消置顶会话（仅影响自己的列表） */
export function pinConversation(conversationId, pinned = true) {
  return post(
    `/api/v1/messages/conversations/${conversationId}/pin`,
    { pinned },
    { showError: false },
  )
}

/** 删除会话（仅对自己隐藏，收到新消息后自动恢复） */
export function deleteConversation(conversationId) {
  return del(`/api/v1/messages/conversations/${conversationId}`, {}, { showError: false })
}

/** 私信总未读数 */
export function unreadCount() {
  return get('/api/v1/messages/unread-count')
}

/** 创建群聊（memberIds 不含自己，至少 1 位成员） */
export function createGroup(name, memberIds) {
  return post('/api/v1/messages/groups', { name, memberIds }, { showError: false })
}

/** 群详情（群名/群主/成员数） */
export function groupDetail(conversationId) {
  return get(`/api/v1/messages/groups/${conversationId}`)
}

/** 修改群设置（群名/群公告/群头像，仅群主） */
export function updateGroup(conversationId, data) {
  return put(`/api/v1/messages/groups/${conversationId}`, data, { showError: false })
}

/** 修改群名称（仅群主，updateGroup 的快捷封装） */
export function renameGroup(conversationId, name) {
  return updateGroup(conversationId, { name })
}

/** 群成员列表 */
export function groupMembers(conversationId) {
  return get(`/api/v1/messages/groups/${conversationId}/members`)
}

/** 邀请群成员（仅群主） */
export function addGroupMembers(conversationId, memberIds) {
  return post(
    `/api/v1/messages/groups/${conversationId}/members`,
    { memberIds },
    { showError: false },
  )
}

/** 移除群成员 / 退出群聊（targetUserId 传自己即退群） */
export function removeGroupMember(conversationId, targetUserId) {
  return del(
    `/api/v1/messages/groups/${conversationId}/members/${targetUserId}`,
    {},
    { showError: false },
  )
}

export default {
  ensureConversation,
  listConversations,
  listMessages,
  searchMessages,
  sendMessage,
  deleteMessages,
  report,
  recallMessage,
  markRead,
  pinConversation,
  deleteConversation,
  unreadCount,
  createGroup,
  groupDetail,
  renameGroup,
  updateGroup,
  groupMembers,
  addGroupMembers,
  removeGroupMember,
}
