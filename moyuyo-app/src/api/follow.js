// 关注体系 API
import { get, post, del } from '@/utils/request'

/** 关注用户 */
export function follow(targetId) {
  return post('/api/v1/follows', { targetId })
}

/** 取消关注 */
export function unfollow(targetId) {
  return del(`/api/v1/follows/${targetId}`)
}

/**
 * 查询当前登录用户是否已关注 targetId。
 * 后端: GET /api/v1/follows/{targetId}/status -> { following: boolean }
 * 用于帖子详情页头部关注按钮的初始状态判断。
 */
export function followStatus(targetId) {
  return get(`/api/v1/follows/${targetId}/status`)
}

/** 我关注的人列表 */
export function listFollowing(params = {}) {
  return get('/api/v1/follows/following', params)
}

/** 关注我的人（粉丝）列表 */
export function listFollowers(params = {}) {
  return get('/api/v1/follows/followers', params)
}

export default { follow, unfollow, followStatus, listFollowing, listFollowers }

// 兼容 follow-list.vue 的命名导入方式（import { followApi } from '@/api/follow'）
export const followApi = { follow, unfollow, followStatus, listFollowing, listFollowers }
