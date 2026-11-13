/**
 * 支付封装（uni 官方 API）
 *
 * 原 MOYUYO-Payment 原生插件已移除（桩实现），改为 uni.requestPayment：
 * - PayPal：provider = 'paypal'（App 端，需在 manifest 配置 Payment.paypal）
 * - Apple Pay / Stripe：uni 无官方通道，继续走 WebView 收银台
 */
/* eslint-disable no-unreachable */
import type { PaymentPluginProtocol, PaymentChannel, PluginResult, PaymentResult } from './types'

/** 官方 API 支持的支付渠道 */
const OFFICIAL_CHANNELS: PaymentChannel[] = ['paypal']

/**
 * 调起 PayPal 官方支付通道
 */
function requestPayPal(orderInfo: Record<string, any>): Promise<PaymentResult> {
  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    if (!orderInfo?.clientId || !orderInfo?.orderId) {
      reject(new Error('缺少 PayPal clientId / orderId'))
      return
    }
    uni.requestPayment({
      provider: 'paypal',
      orderInfo,
      success: (res) => {
        let transactionId = orderInfo.orderId
        try {
          const raw = typeof res.rawdata === 'string' ? JSON.parse(res.rawdata) : res.rawdata
          if (raw && raw.orderId) transactionId = raw.orderId
        } catch {
          // rawdata 解析失败时沿用订单号
        }
        resolve({
          transactionId,
          channel: 'paypal',
          amount: orderInfo.amount || 0,
          currency: orderInfo.currency || 'USD',
          timestamp: Date.now(),
        })
      },
      fail: (err) => reject(new Error(err.errMsg || 'PayPal 支付失败')),
    })
  })
  // #endif
  // #ifndef APP-PLUS
  return Promise.reject(new Error('PayPal 原生支付仅支持 App 端'))
  // #endif
}

/**
 * 支付调用封装
 */
export function usePaymentPlugin(): PaymentPluginProtocol {
  return {
    pay: async (
      channel: PaymentChannel,
      orderInfo: Record<string, any>,
    ): Promise<PluginResult<PaymentResult>> => {
      if (channel !== 'paypal') {
        return { success: false, error: '该渠道无官方 API，请走 WebView 收银台' }
      }
      try {
        return { success: true, data: await requestPayPal(orderInfo) }
      } catch (error: any) {
        return { success: false, error: error?.message || '支付失败' }
      }
    },
    isAvailable: async (channel: PaymentChannel): Promise<PluginResult<boolean>> => {
      return { success: true, data: OFFICIAL_CHANNELS.includes(channel) }
    },
  }
}
