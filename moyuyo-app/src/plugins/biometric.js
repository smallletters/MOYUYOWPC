/**
 * 生物识别原生插件调用封装
 * 插件标识: MOYUYO-Biometric
 * 功能: Face ID / Touch ID 生物识别
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Biometric'

/**
 * 检查当前设备是否支持生物识别
 * @returns {Promise<Object>} { available, biometricType }
 * biometricType: FACE_ID | TOUCH_ID | FINGERPRINT | NONE
 */
export function isBiometricAvailable() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    plugin.isAvailable({}, (result) => {
      resolve({
        available: !!result.available,
        biometricType: result.biometricType || 'NONE',
      })
    })
  })
  // #endif
  return Promise.resolve({ available: false, biometricType: 'NONE' })
}

/**
 * 执行生物识别认证
 * @param {Object} [options]
 * @param {String} [options.reason] 认证提示语
 * @param {Boolean} [options.fallbackToPasscode] 是否允许回退到锁屏密码
 * @returns {Promise<Boolean>}
 */
export function authenticateBiometric(options = {}) {
  const { reason = t('biometricPlugin.prompt'), fallbackToPasscode = true } = options

  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve, reject) => {
    plugin.authenticate({ reason, fallbackToPasscode }, (result) => {
      if (result.success) {
        resolve(true)
      } else {
        reject(new Error(result.message || t('biometricPlugin.failed')))
      }
    })
  })
  // #endif

  // #ifdef H5
  // H5 降级: 使用 WebAuthn API
  if (window.PublicKeyCredential) {
    return Promise.reject(new Error(t('biometricPlugin.noDevice')))
  }
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error(t('biometricPlugin.unsupported')))
  /* #endif */
}

/**
 * 检查生物识别是否已录入（设备是否已注册指纹/面容）
 * @returns {Promise<Boolean>}
 */
export function isBiometricEnrolled() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  return new Promise((resolve) => {
    plugin.isEnrolled({}, (result) => {
      resolve(!!result.enrolled)
    })
  })
  // #endif
  return Promise.resolve(false)
}

export default { isBiometricAvailable, authenticateBiometric, isBiometricEnrolled }
