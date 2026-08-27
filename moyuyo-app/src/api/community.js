import { get, post, del } from '@/utils/request'

/**
 * 社区 API（仅包含后端已实现的接口）。
 * 后端 CommunityController 当前支持：
 *  - GET    /api/v1/community/posts
 *  - GET    /api/v1/community/posts/{id}
 *  - POST   /api/v1/community/posts
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

export function createPost(content, images, topic) {
  const params = { content }
  if (images && images.length) params.images = images
  if (topic) params.topic = topic
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
 */
export function getCommunityTopics() {
  return get('/api/v1/community/topics')
}

export default {
  getCommunityPosts,
  getPostDetail,
  createPost,
  getMyPosts,
  likePost,
  unlikePost,
  addComment,
  collectPost,
  uncollectPost,
  getCommunityTopics,
}
