import { get, post } from '@/utils/request'

/**
 * 按类型分组的任务列表（后端 /missions/grouped）：
 * { daily: [], weekly: [], achievements: [] }
 */
export function getGroupedMissions() {
  return get('/api/v1/missions/grouped')
}

export function getMissions(params = {}) {
  return get('/api/v1/missions', params)
}

export function claimMission(id) {
  return post(`/api/v1/missions/${id}/claim`)
}

export function getMissionStats() {
  return get('/api/v1/missions/stats')
}

export default {
  getGroupedMissions,
  getMissions,
  claimMission,
  getMissionStats,
}
