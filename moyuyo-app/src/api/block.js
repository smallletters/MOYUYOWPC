// 黑名单 API
import { get, post, del } from '@/utils/request'

export function listBlocks(params = {}) {
  return get('/api/v1/blocks', params)
}

export function blockUser(targetId) {
  return post('/api/v1/blocks', { targetId })
}

export function unblockUser(id) {
  return del(`/api/v1/blocks/${id}`)
}

export default { listBlocks, blockUser, unblockUser }