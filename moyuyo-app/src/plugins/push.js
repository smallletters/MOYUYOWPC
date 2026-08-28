/**
 * 推送通知原生插件调用封装
 * 插件标识: MOYUYO-Push
 * 功能: FCM / APNs 设备注册、通知接收、角标管理
 */
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Push'

/**
 * 注册推送服务
 * @returns {Promise<String>} push token
 */
export function registerPush() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.register({}, (result) => {
      if (result.success) {
        resolve(result.token)
      } else {
        reject(new Error(result.message || '推送注册失败'))
      }
    })
  })
  // #endif

  // #ifdef H5
  // H5 降级: 使用 Web Push API
  if ('Notification' in window) {
    Notification.requestPermission()
  }
  return Promise.resolve('')
  // #endif
}

/**
 * 获取已注册的 push token
 * @returns {Promise<String>}
 */
export function getPushToken() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    plugin.getToken({}, (result) => {
      resolve(result.token || '')
    })
  })
  // #endif
  return Promise.resolve('')
}

/**
 * 处理接收到的推送消息
 * @param {Object} message 推送消息体
 * @returns {Promise<void>}
 */
export function handlePushMessage(message) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    plugin.handleMessage(message, () => resolve())
  })
  // #endif
  return Promise.resolve()
}

/**
 * 设置应用角标数
 * @param {Number} count 角标数字
 */
export function setBadgeCount(count) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.setBadge({ count }, () => {})
  // #endif
}

/**
 * 清空角标
 */
export function clearBadge() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  plugin.clearBadge({}, () => {})
  // #endif
}

/**
 * 设置/获取推送通知开关状态
 * @param {Boolean} [enabled] 传入则设置，不传则查询
 * @returns {Promise<Boolean>}
 *
 * H5 端语义:
 * - 设置时调用浏览器 Notification API;granted 返回 true,denied/default 返回 false
 * - 查询时按 Notification.permission 反映
 */
export function togglePushNotification(enabled) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    if (enabled !== undefined) {
      plugin.setEnabled({ enabled }, (result) => resolve(!!result.success))
    } else {
      plugin.isEnabled({}, (result) => resolve(!!result.enabled))
    }
  })
  // #endif

  // #ifdef H5
  if (typeof window === 'undefined' || typeof Notification === 'undefined') {
    // 当前环境无 Notification API(如非 https / 旧浏览器),视为失败
    return Promise.resolve(false)
  }
  if (enabled === undefined) {
    return Promise.resolve(Notification.permission === 'granted')
  }
  if (!enabled) {
    // H5 端不支持"代码关闭",只能由用户在浏览器设置里撤销
    return Promise.resolve(true)
  }
  if (Notification.permission === 'granted') return Promise.resolve(true)
  if (Notification.permission === 'denied') return Promise.resolve(false)
  // default: 主动请求权限
  return Notification.requestPermission().then(
    (perm) => perm === 'granted',
    () => false,
  )
  // #endif

  // #ifdef MP-WEIXIN
  // 小程序端订阅消息需独立授权流程,这里统一返回 false 让上层走友好提示
  return Promise.resolve(false)
  // #endif
}

export default {
  registerPush,
  getPushToken,
  handlePushMessage,
  setBadgeCount,
  clearBadge,
  togglePushNotification,
}
