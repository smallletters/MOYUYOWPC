import { get } from '@/utils/request'

export function getInviteCode() {
  return get('/api/v1/invites/code')
}

export function getInviteStats() {
  return get('/api/v1/invites/stats')
}

export function getInviteHistory(params = {}) {
  return get('/api/v1/invites/history', params)
}

export function getInviteRanking() {
  return get('/api/v1/invites/ranking')
}

export default {
  getInviteCode,
  getInviteStats,
  getInviteHistory,
  getInviteRanking,
}
