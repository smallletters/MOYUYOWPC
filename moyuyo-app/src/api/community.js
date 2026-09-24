import { get, post, del } from '@/utils/request'

/**
 * 社区 API（仅包含后端已实现的接口）。
 * 后端 CommunityController 当前支持：
 *  - GET    /api/v1/community/posts
 *  - GET    /api/v1/community/posts/{id}
 *  - POST   /api/v1/community/posts
 *  - DELETE /api/v1/community/posts/{id}     删除帖子（仅作者本人）
 *  - GET    /api/v1/community/posts/mine
 *  - POST   /api/v1/community/posts/{id}/like
 *  - DELETE /api/v1/community/posts/{id}/like
 *  - POST   /api/v1/community/posts/{postId}/comments
 *  - GET    /api/v1/community/topics           话题列表（用于发布页选择话题）
 *  - POST   /api/v1/community/posts/{id}/collect  收藏帖子
 *  - DELETE /api/v1/community/posts/{id}/collect 取消收藏
 */

export function getCommunityPosts(params = {}) {
  return get('/api/v1/community/posts', params)
}

/**
 * 按 userId 拉取指定用户已发布的帖子（用于他人 profile 页的"帖子" tab）。
 * 后端会忽略 keyword/topic 参数,只按 userId 过滤 status=1 的帖子。
 */
export function getUserPosts(userId, params = {}) {
  return get('/api/v1/community/posts', { userId, ...params })
}

/**
 * 搜索帖子：等价于 getCommunityPosts({ keyword })，但走专用 /search 端点便于后端日志/限流区分。
 */
export function searchCommunityPosts(params = {}) {
  return get('/api/v1/community/search', params)
}

/**
 * 关注 Tab 数据源：当前登录用户关注的人发布的帖子（按时间倒序分页）。
 */
export function getFollowFeed(params = {}) {
  return get('/api/v1/follows/feed', params)
}

export function getPostDetail(id) {
  return get(`/api/v1/community/posts/${id}`)
}

export function createPost(content, images, video, cover, topic, scheduledAt, petId) {
  const params = { content }
  // 视频与图片互斥：优先视频
  if (video) {
    params.video = video
    if (cover) params.cover = cover
  } else if (images && images.length) {
    params.images = images
  }
  if (topic) params.topic = topic
  // 定时发布时间:可选,ISO 字符串
  if (scheduledAt) params.scheduledAt = scheduledAt
  // 关联宠物:可选,用于把帖子挂到宠物记忆树
  if (petId) params.petId = petId
  return post('/api/v1/community/posts', params)
}

export function getMyPosts(params = {}) {
  return get('/api/v1/community/posts/mine', params)
}

export function likePost(id) {
  return post(`/api/v1/community/posts/${id}/like`)
}

export function unlikePost(id) {
  return del(`/api/v1/community/posts/${id}/like`)
}

export function addComment(postId, content, parentId) {
  const params = { content }
  if (parentId) params.parentId = parentId
  return post(`/api/v1/community/posts/${postId}/comments`, params)
}

/**
 * 删除帖子（仅作者本人）。后端会校验：
 *  - 当前用户 == 帖子作者 userId（否则 403）
 *  - 帖子 status == 1（已发布；待发布/隐藏不可由用户删）
 * 级联清理：评论(逻辑删除)、点赞、收藏(物理删除)。
 * 注意：审核记录(mo_content_review)后端保留不动。
 */
export function deletePost(id) {
  return del(`/api/v1/community/posts/${id}`)
}

/**
 * 收藏/取消收藏帖子：用于帖子详情页心形按钮。
 */
export function collectPost(id) {
  return post(`/api/v1/community/posts/${id}/collect`)
}

export function uncollectPost(id) {
  return del(`/api/v1/community/posts/${id}/collect`)
}

/**
 * 话题列表（社区广场）：按热度降序+排序权重升序，返回活跃话题。
 * 用于发布页"话题"cell 弹层选项。
 * 传入 keyword 时按话题名模糊匹配（社区搜索页专用）。
 */
export function getCommunityTopics(params = {}) {
  return get('/api/v1/community/topics', params)
}

/**
 * 用户搜索：按昵称模糊匹配，返回公开字段(id/nickname/avatar)。
 * 用于社区搜索页"用户"Tab。
 */
export function searchCommunityUsers(params = {}) {
  return get('/api/v1/users/search', params)
}

/**
 * 当前用户收藏的帖子列表（"我的"页→收藏 入口）。
 * 后端: GET /api/v1/community/posts/collected
 */
export function getCollectedPosts(params = {}) {
  return get('/api/v1/community/posts/collected', params)
}

/**
 * 当前用户点赞过的帖子列表（按点赞时间倒序）。
 * 后端: GET /api/v1/community/posts/liked
 * 用于"我的"页 → 个人中心 → 点赞 Tab。
 * 返回的 VO.liked 恒为 true；VO.collected 反映当前是否已收藏。
 */
export function getLikedPosts(params = {}) {
  return get('/api/v1/community/posts/liked', params)
}

/**
 * 实时敏感词检查:返回命中的敏感词字符串列表(去重保序)。
 * 用于发帖 / 评论时实时高亮提示,不阻断提交。
 * 后端: GET /api/v1/community/sensitive-check?text=xxx
 */
export function sensitiveCheck(text) {
  return get('/api/v1/community/sensitive-check', { text })
}

export default {
  getCommunityPosts,
  getUserPosts,
  searchCommunityPosts,
  searchCommunityUsers,
  getPostDetail,
  createPost,
  deletePost,
  getMyPosts,
  likePost,
  unlikePost,
  addComment,
  collectPost,
  uncollectPost,
  getCollectedPosts,
  getLikedPosts,
  getCommunityTopics,
  // 关注 Tab 真实数据源（关注的人发布的帖子分页），由 default 导出暴露给 communityApi
  getFollowFeed,
}
