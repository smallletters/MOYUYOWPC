/**
 * 优惠券 API
 * 公开数据，仅需 WooCommerce Basic Auth，不需要用户 Token
 */
import { get } from '@/utils/request'

const BASE = '/wc/v3/coupons'

/**
 * 获取可用优惠券列表
 */
export function getCouponList(params = {}) {
  return get(BASE, { per_page: 50, ...params }, { useAuth: true, useOAuth: false })
}

/**
 * 获取优惠券详情
 */
export function getCouponDetail(id) {
  return get(`${BASE}/${id}`, {}, { useAuth: true, useOAuth: false })
}

/**
 * 通过 code 查询优惠券
 */
export function getCouponByCode(code) {
  return get(BASE, { code }, { useAuth: true, useOAuth: false })
}

export default {
  getCouponList,
  getCouponDetail,
  getCouponByCode
}
