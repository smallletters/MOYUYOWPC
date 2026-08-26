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

/** 我关注的人列表 */
export function listFollowing(params = {}) {
  return get('/api/v1/follows/following', params)
}

/** 关注我的人（粉丝）列表 */
export function listFollowers(params = {}) {
  return get('/api/v1/follows/followers', params)
}

export default { follow, unfollow, listFollowing, listFollowers }