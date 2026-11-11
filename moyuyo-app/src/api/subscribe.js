import { get, post, put } from '@/utils/request'

export function getSubscribePlans() {
  return get('/api/v1/subscriptions/plans')
}

/**
 * 创建订阅订单（待支付），返回 OrderEntity（含 orderNo），
 * 需再调用订单支付接口完成付款，支付成功回调后服务端才开通/续期订阅
 */
export function subscribe(planId) {
  return post('/api/v1/subscriptions/subscribe', { planId })
}

export function getMySubscription() {
  return get('/api/v1/subscriptions/my')
}

export function toggleAutoRenew(autoRenew) {
  return put('/api/v1/subscriptions/auto-renew', { autoRenew })
}

export function getSubscribeHistory(params = {}) {
  return get('/api/v1/subscriptions/history', params)
}

export default {
  getSubscribePlans,
  subscribe,
  getMySubscription,
  toggleAutoRenew,
  getSubscribeHistory,
}
