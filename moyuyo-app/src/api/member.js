import { get, post } from '@/utils/request'

export function getMemberInfo() {
  return get('/api/v1/member')
}

export function getPointsLog(params = {}) {
  return get('/api/v1/member/points-log', params)
}

export function getWallet() {
  return get('/api/v1/member/wallet')
}

export function recharge(amount, channel) {
  return post('/api/v1/member/recharge', { amount, channel })
}

/**
 * 提现申请
 * 后端契约:POST /api/v1/member/withdraw
 * 入参:{ amount: Number, accountId?: String }
 * 返回:Result<{ withdrawId, status, estimatedArrival }>
 */
export function withdraw(amount, accountId) {
  return post('/api/v1/member/withdraw', { amount, accountId })
}

export function getMemberLevels() {
  return get('/api/v1/member/levels')
}

export function getPointsRate() {
  return get('/api/v1/member/points-rate')
}

// 会员专属特权列表（按等级过滤）
export function getMemberPrivileges() {
  return get('/api/v1/member/privileges')
}

export default {
  getMemberInfo,
  getPointsLog,
  getWallet,
  recharge,
  withdraw,
  getMemberLevels,
  getPointsRate,
  getMemberPrivileges,
}
