/**
 * 订单 API
 * - 创建订单：APP 内创建 pending 订单，然后 WebView 跳转 WP 支付页（方案A）
 * - 查询订单：用户订单列表 / 订单详情
 *
 * 鉴权方式：
 *   useAuth=true  →  WooCommerce Consumer Key Basic Auth
 *   useOAuth=true →  WP OAuth Server Bearer Token（用户身份）
 */
import { get, post, put } from '@/utils/request'

const BASE = '/wc/v3/orders'

/**
 * 创建订单
 */
export function createOrder(orderData) {
  return post(BASE, orderData, { useAuth: true, useOAuth: false })
}

/**
 * 获取订单列表（按客户 ID 筛选）
 */
export function getOrderList(params = {}) {
  return get(BASE, { per_page: 20, ...params }, { useAuth: true, useOAuth: true })
}

/**
 * 获取订单详情
 */
export function getOrderDetail(id) {
  return get(`${BASE}/${id}`, {}, { useAuth: true, useOAuth: true })
}

/**
 * 取消订单
 */
export function cancelOrder(id, reason = '') {
  return put(`${BASE}/${id}`, {
    status: 'cancelled',
    meta_data: [{ key: '_cancel_reason', value: reason }]
  }, { useAuth: true, useOAuth: true })
}

/**
 * 支付页面 URL（用于 WebView 跳转，方案A）
 */
export function getPayUrl(orderId) {
  return `/checkout/order-pay/${orderId}/`
}

export default {
  createOrder,
  getOrderList,
  getOrderDetail,
  cancelOrder,
  getPayUrl
}
