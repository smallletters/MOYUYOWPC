import { get, post, del } from '@/utils/request'

export function createOrder(data) {
  return post('/api/v1/orders', data)
}

/**
 * 创建预售订单（全款/定金）。
 * 价格、限购、预售库存均由服务端按 mo_product_presale 配置计算，前端不传金额。
 */
export function createPresaleOrder(data) {
  return post('/api/v1/orders/presale', data)
}

/** 支付尾款：为已支付定金的订单生成尾款单（服务端幂等） */
export function createBalanceOrder(id) {
  return post(`/api/v1/orders/${id}/presale-balance`)
}

export function getOrderList(params = {}) {
  return get('/api/v1/orders', params)
}

export function getOrderDetail(id) {
  return get(`/api/v1/orders/${id}`)
}

export function cancelOrder(id, reason) {
  return post(`/api/v1/orders/${id}/cancel`, { reason })
}

export function confirmReceived(id) {
  return post(`/api/v1/orders/${id}/confirm`)
}

export function deleteOrder(id) {
  return del(`/api/v1/orders/${id}`)
}

export function createPayment(data) {
  // 关闭 request 默认 showLoading：pay.vue 自身已用 mask loading 控制 UI，
  // 避免与 request 内部默认 loading 嵌套触发"showLoading/hideLoading 必须配对"告警。
  return post('/api/v1/payments/create', data, { showLoading: false })
}

/**
 * 获取后台「系统配置 - 支付设置」中开启的支付方式（收银台动态展示的数据源）
 * 返回 [{ code, channel, name, desc }]，code 为 CARD / GOOGLE_PAY / APPLE_PAY / PAYPAL
 */
export function getAvailablePaymentMethods() {
  // 不展示 loading：收银台已有整页 loading，避免重复弹层
  return get('/api/v1/payments/methods', {}, { showLoading: false })
}

export function getLogistics(orderId) {
  return get(`/api/v1/logistics/order/${orderId}`)
}

export function confirmReceivedLogistics(orderId) {
  return post(`/api/v1/logistics/${orderId}/confirm`)
}

export function applyRefund(data) {
  return post('/api/v1/refunds', data)
}

export function getRefundDetail(id) {
  return get(`/api/v1/refunds/${id}`)
}

export function getMyRefunds(params = {}) {
  return get('/api/v1/refunds/mine', params)
}

export function getShippingRate(country, weight) {
  return get('/api/v1/shipping/estimate', { country, weight })
}

/**
 * 查询某国家所有可用配送方式及实时运费（新版，后端权威）
 * @param {object} payload
 * @param {string} payload.country        ISO 3166-1 alpha-2 大写
 * @param {Array}  payload.items          [{ productId, skuId, quantity, weightGrams }]
 * @param {number} payload.subtotal       满减前商品总金额（USD）
 * @param {string} [payload.currency]     默认 USD
 * @returns {Promise<Array<ShippingMethodVO>>} 各配送方式的实时运费
 */
export function getShippingMethods(payload) {
  return post('/api/v1/shipping/methods', payload)
}

export default {
  createOrder,
  createPresaleOrder,
  createBalanceOrder,
  getOrderList,
  getOrderDetail,
  cancelOrder,
  confirmReceived,
  deleteOrder,
  createPayment,
  getLogistics,
  confirmReceivedLogistics,
  applyRefund,
  getRefundDetail,
  getMyRefunds,
  getShippingRate,
  getShippingMethods,
}
