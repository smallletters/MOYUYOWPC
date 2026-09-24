/**
 * 支付原生插件封装
 *
 * 调用端通过 uni.requireNativePlugin('MOYUYO-Payment') 调用原生支付。
 * 降级策略：H5 端回退到 WebView 支付。
 */
import type { PaymentPluginProtocol, PaymentChannel, PluginResult, PaymentResult } from './types'

// 与原生 dcloud_uniplugins.json 中声明的插件名保持一致（"MOYUYO-Payment"）
const PLUGIN_NAME = 'MOYUYO-Payment'

let plugin: any = null

function getPlugin(): any {
  if (!plugin) {
    try {
      plugin = uni.requireNativePlugin(PLUGIN_NAME)
    } catch {
      console.warn('[Payment] 原生插件不可用')
      return null
    }
  }
  return plugin
}

function isNativeAvailable(): boolean {
  // #ifdef APP-PLUS
  return !!getPlugin()
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
}

/**
 * 支付插件调用封装
 * - pay: 适配协议入参 { channel, orderNo, amount, currency }
 *   转换为原生侧 pay 要求的 { orderNo, payMethod, amount, currency }
 * - isAvailable: 走原生 getAvailableChannels，返回渠道是否包含目标 channel
 */
export function usePaymentPlugin(): PaymentPluginProtocol {
  // 通用调用：T 默认 void，可按调用点显式标注返回 data 的类型
  const call = <T = void>(method: string, args?: any): Promise<PluginResult<T>> => {
    return new Promise((resolve) => {
      if (!isNativeAvailable()) {
        resolve({ success: false, error: '原生支付插件不可用' } as PluginResult<T>)
        return
      }
      getPlugin()[method](args, (res: any) => {
        resolve(res as PluginResult<T>)
      })
    })
  }

  // 内部专用：拉取可用渠道列表（原生返回 { channels: string[] }）
  const getAvailableChannels = (): Promise<PluginResult<{ channels: string[] }>> => {
    return new Promise((resolve) => {
      if (!isNativeAvailable()) {
        resolve({ success: false, error: '原生支付插件不可用' } as PluginResult<{ channels: string[] }>)
        return
      }
      getPlugin()['getAvailableChannels']({}, (res: any) => {
        resolve(res as PluginResult<{ channels: string[] }>)
      })
    })
  }

  return {
    pay: (channel: PaymentChannel, orderInfo: Record<string, any>) =>
      call<PaymentResult>('pay', {
        // 协议入参用 channel，原生 pay 用 payMethod；做一次映射
        orderNo: orderInfo.orderNo,
        payMethod: channel,
        amount: orderInfo.amount,
        currency: orderInfo.currency || 'USD',
      }),
    // 通过 getAvailableChannels 判断目标 channel 是否在原生支持的列表中
    isAvailable: async (channel: PaymentChannel): Promise<PluginResult<boolean>> => {
      const result = await getAvailableChannels()
      if (!result.success) {
        return { success: false, error: result.error || '查询渠道失败' }
      }
      const list = (result.data && result.data.channels) || []
      return { success: true, data: list.includes(channel) }
    },
  }
}
