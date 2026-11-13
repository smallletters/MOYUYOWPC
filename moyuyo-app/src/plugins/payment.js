/**
 * 支付调用封装（uni 官方 API）
 *
 * 原 MOYUYO-Payment 原生插件已移除（桩实现），改为 uni.requestPayment：
 * - PayPal：provider = 'paypal'（App 端，需在 manifest 配置 Payment.paypal）
 * - Apple Pay / Stripe：uni 无官方通道，继续走 WebView 收银台
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

/** 官方 API 支持的支付渠道 */
const OFFICIAL_CHANNELS = ['PAYPAL']

/** 统一渠道标识格式：apple-pay / apple_pay → APPLE_PAY */
function normalizeChannel(channel) {
  return String(channel || '')
    .toUpperCase()
    .replace(/-/g, '_')
}

/**
 * 获取当前设备支持的支付渠道
 * @returns {Promise<String[]>} 渠道列表 ['PAYPAL']
 */
export function getPaymentChannels() {
  // #ifdef APP-PLUS
  return Promise.resolve(OFFICIAL_CHANNELS)
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.resolve([])
  /* #endif */
}

/**
 * 发起支付
 * @param {Object} params
 * @param {String} params.orderNo 订单号
 * @param {String} params.payMethod 支付方式，官方通道仅 PAYPAL
 * @param {Object} params.orderInfo PayPal 下单参数 { clientId, orderId, environment, currency }
 * @returns {Promise<Object>} { transactionId }
 */
export function createPay(params = {}) {
  const { payMethod, orderInfo } = params
  if (normalizeChannel(payMethod) !== 'PAYPAL') {
    return Promise.reject(new Error(t('paymentPlugin.unsupported')))
  }
  if (!orderInfo || !orderInfo.clientId || !orderInfo.orderId) {
    return Promise.reject(new Error(t('paymentPlugin.payFailed')))
  }

  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'paypal',
      orderInfo,
      success: (res) => {
        let transactionId = orderInfo.orderId
        try {
          const raw = typeof res.rawdata === 'string' ? JSON.parse(res.rawdata) : res.rawdata
          if (raw && raw.orderId) transactionId = raw.orderId
        } catch (e) {
          // rawdata 解析失败时沿用订单号
        }
        resolve({ transactionId })
      },
      fail: (err) => reject(new Error(err.errMsg || t('paymentPlugin.payFailed'))),
    })
  })
  // #endif

  /* #ifndef APP-PLUS */
  return Promise.reject(new Error(t('paymentPlugin.unsupported')))
  /* #endif */
}

/**
 * 查询订单支付状态
 * uni 无官方支付状态 API，需调用后端订单接口
 * @returns {Promise<never>}
 */
export function queryPaymentStatus() {
  return Promise.reject(new Error(t('paymentPlugin.unsupported')))
}

/**
 * 处理支付回调
 * 官方通道的结果由 uni.requestPayment 回调直接返回，无需额外转发
 */
export function handlePaymentCallback() {
  // no-op
}

/**
 * 支付调用封装（composable）
 * @returns {{ pay: Function, isAvailable: Function }}
 */
export function usePaymentPlugin() {
  return {
    pay: (channel, orderInfo) =>
      createPay({
        orderNo: orderInfo?.orderNo,
        payMethod: normalizeChannel(channel),
        amount: orderInfo?.amount,
        currency: orderInfo?.currency || 'USD',
        orderInfo,
      })
        .then((result) => ({ success: true, data: result }))
        .catch((error) => ({ success: false, error: error.message })),
    isAvailable: async (channel) => {
      const list = await getPaymentChannels()
      return { success: true, data: list.includes(normalizeChannel(channel)) }
    },
  }
}

export default {
  createPay,
  getPaymentChannels,
  queryPaymentStatus,
  handlePaymentCallback,
  usePaymentPlugin,
}
