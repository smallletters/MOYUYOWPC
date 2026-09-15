import { get, post } from '@/utils/request'

/** 分页查询积分流水 */
export function getPointsLog(params = {}) {
  return get('/api/v1/points/log', params)
}

/** 查询当前积分余额 */
export function getPointsBalance() {
  return get('/api/v1/points/balance')
}

/** 每日签到 */
export function checkin() {
  return post('/api/v1/points/checkin')
}

/** 漏签补签（消耗 50 积分，每月第 1 次免费）；date 为被补签日期 yyyy-MM-dd，不传则补当月最近漏签日 */
export function makeupCheckin(date) {
  // 后端用 @RequestParam 接收日期，走 query string
  const query = date ? `?date=${encodeURIComponent(date)}` : ''
  return post(`/api/v1/points/checkin/makeup${query}`)
}

/** 签到日历（指定月份的已签到日期 + 当前连续签到天数）；month 为 yyyy-MM，不传则取当月 */
export function getCheckinCalendar(params = {}) {
  return get('/api/v1/points/checkin/calendar', params)
}

/** 查询积分商城礼品列表（来自 /api/v1/points/goods） */
export function getPointsGoods(params = {}) {
  return get('/api/v1/points/goods', params)
}

/** 兑换积分礼品（积分扣减 + 写流水） */
export function exchangePointsGoods(goodsId, addressId) {
  return post('/api/v1/points/goods/exchange', { goodsId, addressId })
}

export default {
  getPointsLog,
  getPointsBalance,
  checkin,
  makeupCheckin,
  getCheckinCalendar,
  getPointsGoods,
  exchangePointsGoods,
}
