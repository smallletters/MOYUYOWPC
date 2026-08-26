import { get, post, put, del } from '@/utils/request'

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

export function getMemberLevels() {
  return get('/api/v1/member/levels')
}

export function getPointsRate() {
  return get('/api/v1/member/points-rate')
}

export default {
  getMemberInfo,
  getPointsLog,
  getWallet,
  recharge,
  getMemberLevels,
  getPointsRate,
}
