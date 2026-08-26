import { get, post } from '@/utils/request'

/** 抽奖活动列表（启用的） */
export function getLotteries() {
  return get('/api/v1/lotteries')
}

/**
 * 单次抽奖。后端路径 /api/v1/lotteries/{id}/spin：
 * - 当天有免费次数：pointsSpent=0
 * - 免费次数用完：扣积分 cost
 * - 中奖且奖品名含 "N积分"：自动加 N 到用户余额
 */
export function spinLottery(id) {
  return post(`/api/v1/lotteries/${id}/spin`)
}

export function getLotteryHistory() {
  return get('/api/v1/lotteries/history')
}

export function getLotteryStats() {
  return get('/api/v1/lotteries/stats')
}

/**
 * 兼容旧版调用（不带 id）
 */
export function spin() {
  return post('/api/v1/lotteries/spin')
}

export default {
  getLotteries,
  spinLottery,
  getLotteryHistory,
  getLotteryStats,
  spin,
}