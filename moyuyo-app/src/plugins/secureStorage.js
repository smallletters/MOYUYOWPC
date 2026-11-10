/**
 * 加密存储原生插件调用封装
 * 插件标识: MOYUYO-SecureStorage
 * 功能: refreshToken 等长效凭证的加密落盘
 *   - Android: EncryptedSharedPreferences（密钥托管在 Android Keystore）
 *   - iOS: Keychain（kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly）
 *
 * 注意: H5 / 小程序 / 未打自定义基座的环境没有该插件，
 * 所有方法都会返回失败结果，由上层 secureToken.js 降级为明文存储。
 */
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-SecureStorage'

/** 插件调用超时：原生回调异常不返回时，避免 Promise 永久挂起卡住登录链路 */
const CALL_TIMEOUT_MS = 3000

/** 获取原生插件实例；非 APP 端或插件未集成时返回 null */
function getPlugin() {
  // #ifdef APP-PLUS
  try {
    return uni.requireNativePlugin(PLUGIN_NAME)
  } catch (e) {
    console.warn('[secureStorage] requireNativePlugin failed:', e?.message)
    return null
  }
  // #endif
  return null
}

/**
 * 统一调用插件方法：回调式转 Promise + 超时兜底
 * @param {string} method 插件方法名
 * @param {object} [params] 入参
 * @returns {Promise<object>} 插件返回的原始结果对象
 */
function callPlugin(method, params = {}) {
  const plugin = getPlugin()
  if (!plugin || typeof plugin[method] !== 'function') {
    return Promise.resolve({ success: false, message: 'plugin unavailable' })
  }
  return new Promise((resolve) => {
    let settled = false
    const finish = (result) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(result || { success: false, message: 'empty result' })
    }
    const timer = setTimeout(() => {
      console.warn('[secureStorage] call timeout:', method)
      finish({ success: false, message: 'timeout' })
    }, CALL_TIMEOUT_MS)
    try {
      plugin[method](params, finish)
    } catch (e) {
      finish({ success: false, message: e?.message })
    }
  })
}

/** 能力探测：插件是否可用（不关心具体存储后端） */
export function isSecureStorageAvailable() {
  return callPlugin('isAvailable').then((r) => !!r.available)
}

/**
 * 读取加密项
 * @returns {Promise<{success: boolean, value: string}>}
 *   success=false 表示插件不可用或存储损坏（调用方应降级），
 *   success=true 且 value='' 表示确实没有存过值
 */
export function readSecureItem(key) {
  return callPlugin('getItem', { key }).then((r) => ({
    success: r.success === true,
    value: r.success ? r.value || '' : '',
  }))
}

/** 写入加密项，返回是否成功 */
export function writeSecureItem(key, value) {
  return callPlugin('setItem', { key, value }).then((r) => r.success === true)
}

/** 删除单个加密项 */
export function removeSecureItem(key) {
  return callPlugin('removeItem', { key }).then((r) => r.success === true)
}

/** 清空全部加密项（登出 / 注销账号时使用） */
export function clearSecureItems() {
  return callPlugin('clear').then((r) => r.success === true)
}

export default {
  isSecureStorageAvailable,
  readSecureItem,
  writeSecureItem,
  removeSecureItem,
  clearSecureItems,
}
