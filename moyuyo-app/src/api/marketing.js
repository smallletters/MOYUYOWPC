// 运营/营销 API 聚合：Prime/分销/成就/新人/活动/预约/报告
import { get, post } from '@/utils/request'

// Prime 会员
export function listPrimePlans() {
  return get('/api/v1/prime/plans')
}

// 分销中心
export function getAffiliateAccount() {
  return get('/api/v1/affiliate/account')
}
export function listCommissions(params = {}) {
  return get('/api/v1/affiliate/commissions', params)
}

// 成就
export function listAchievements() {
  return get('/api/v1/achievements')
}

// 新人礼包
export function listNewuserGifts() {
  return get('/api/v1/newuser/gifts')
}
export function claimGift(giftId) {
  return post(`/api/v1/newuser/gifts/${giftId}/claim`)
}
export function myGifts() {
  return get('/api/v1/newuser/my')
}

// 节日活动
export function listActiveFestivals() {
  return get('/api/v1/festivals/active')
}
export function festivalDetail(id) {
  return get(`/api/v1/festivals/${id}`)
}

// 服务预约
export function listBookings(params = {}) {
  return get('/api/v1/bookings', params)
}
export function bookingDetail(id) {
  return get(`/api/v1/bookings/${id}`)
}
export function createBooking(body) {
  return post('/api/v1/bookings', body)
}
export function cancelBooking(id) {
  return post(`/api/v1/bookings/${id}/cancel`)
}

// 年度报告
export function annualReport() {
  return get('/api/v1/reports/annual')
}

export default {
  listPrimePlans,
  getAffiliateAccount, listCommissions,
  listAchievements,
  listNewuserGifts, claimGift, myGifts,
  listActiveFestivals, festivalDetail,
  listBookings, bookingDetail, createBooking, cancelBooking,
  annualReport,
}