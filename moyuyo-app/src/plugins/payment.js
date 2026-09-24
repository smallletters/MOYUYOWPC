/**
 * 支付原生插件调用封装
 * 插件标识: MOYUYO-Payment
 * 功能: Stripe / PayPal / Apple Pay / Google Pay 原生支付
 */
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Payment'

/**
 * 获取当前设备支持的支付渠道
 * @returns {Promise<String[]>} 渠道列表 ['STRIPE', 'APPLE_PAY', 'GOOGLE_PAY', 'PAYPAL']
 */
export function getPaymentChannels() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    plugin.getAvailableChannels({}, (result) => {
      resolve(result.channels || [])
    })
  })
  // #endif
  // #ifndef APP-PLUS
  return Promise.resolve(['STRIPE', 'PAYPAL'])
  // #endif
}

/**
 * 发起支付
 * @param {Object} params 支付参数
 * @param {String} params.orderNo 订单号
 * @param {String} params.payMethod 支付方式 STRIPE | APPLE_PAY | GOOGLE_PAY | PAYPAL
 * @param {Number} params.amount 金额（分）
 * @param {String} params.currency 币种，默认 USD
 * @returns {Promise<Object>} { success, transactionId }
 */
export function createPay(params) {
  const { orderNo, payMethod, amount, currency = 'USD' } = params

  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.pay(
      {
        orderNo,
        payMethod,
        amount,
        currency,
      },
      (result) => {
        if (result.success) {
          resolve({ transactionId: result.transactionId })
        } else {
          reject(new Error(result.message || '支付失败'))
        }
      },
    )
  })
  // #endif

  // #ifdef H5
  // H5 降级: 跳转 Web 支付页
  uni.navigateTo({ url: `/pages/webview/pay?orderNo=${orderNo}&method=${payMethod}` })
  return Promise.resolve({ transactionId: '' })
  // #endif

  // #ifdef MP-WEIXIN
  // 小程序支付通过 uni.requestPayment 实现
  return Promise.reject(new Error('请使用小程序原生支付'))
  // #endif
}

/**
 * 查询订单支付状态
 * @param {String} orderNo 订单号
 * @returns {Promise<Object>} { status, transactionId }
 */
export function queryPaymentStatus(orderNo) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.queryStatus({ orderNo }, (result) => {
      if (result.success) {
        resolve({ status: result.status, transactionId: result.transactionId })
      } else {
        reject(new Error(result.message || '查询失败'))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持'))
  /* #endif */
}

/**
 * 处理支付回调（Deep Link / URL Scheme）
 * @param {Object} callbackData 回调数据
 */
export function handlePaymentCallback(callbackData) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.handleCallback(callbackData)
  // #endif
}

/**
 * 支付插件调用封装（composable）
 * 与 payment.ts 中 usePaymentPlugin 保持一致
 * - pay: 把协议入参映射为原生 { orderNo, payMethod, amount, currency }
 * - isAvailable: 走原生 getAvailableChannels 二次判断目标 channel 是否被支持
 * @returns {{ pay: Function, isAvailable: Function }}
 */
export function usePaymentPlugin() {
  const call = (method, args) => {
    return new Promise((resolve) => {
      // #ifdef APP-PLUS
      const plugin = uni.requireNativePlugin(PLUGIN_NAME)
      if (!plugin) {
        resolve({ success: false, error: '原生支付插件不可用' })
        return
      }
      plugin[method](args, (res) => resolve(res))
      // #endif
      // #ifndef APP-PLUS
      resolve({ success: false, error: '原生支付插件不可用' })
      // #endif
    })
  }

  // 内部专用：拉取原生支持的渠道列表
  const getAvailableChannels = () => {
    return new Promise((resolve) => {
      // #ifdef APP-PLUS
      const plugin = uni.requireNativePlugin(PLUGIN_NAME)
      if (!plugin) {
        resolve({ success: false, error: '原生支付插件不可用' })
        return
      }
      plugin.getAvailableChannels({}, (res) => resolve(res || {}))
      // #endif
      // #ifndef APP-PLUS
      resolve({ success: false, error: '原生支付插件不可用' })
      // #endif
    })
  }

  return {
    pay: (channel, orderInfo) =>
      call('pay', {
        // 协议入参用 channel，原生 pay 用 payMethod；做一次映射
        orderNo: orderInfo.orderNo,
        payMethod: channel,
        amount: orderInfo.amount,
        currency: orderInfo.currency || 'USD',
      }),
    // 通过 getAvailableChannels 判断目标 channel 是否被原生支持
    isAvailable: async (channel) => {
      const result = await getAvailableChannels()
      if (!result.success) return result
      const list = (result.data && result.data.channels) || []
      return { success: true, data: list.includes(channel) }
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
