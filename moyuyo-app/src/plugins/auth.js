/**
 * 社交登录调用封装（uni 官方 API）
 *
 * 原 MOYUYO-Auth 原生插件已移除（桩实现），改为：
 * - Apple：uni.login({ provider: 'apple' })
 * - Google / Facebook：uni 无 App 端官方能力，走 Web OAuth 降级
 */
import { t } from '@/i18n'
/* eslint-disable no-unreachable */

/**
 * Apple 登录
 * @returns {Promise<Object>} { userId, email, fullName, identityToken }
 */
export function loginWithApple() {
  // #ifdef APP-PLUS
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'apple',
      success: (res) => {
        // Apple 的完整登录信息在 appleInfo（App 3.4.3+），authResult 只有通用认证数据
        const info = res.appleInfo || {}
        const auth = res.authResult || {}
        const fullName = info.fullName
        resolve({
          userId: info.user || auth.openid || '',
          email: info.email || auth.email || '',
          // fullName 是对象（givenName/familyName 等），统一转成展示用字符串
          fullName:
            typeof fullName === 'string'
              ? fullName
              : [fullName?.givenName, fullName?.familyName].filter(Boolean).join(' '),
          identityToken: info.identityToken || auth.identityToken || '',
        })
      },
      fail: (err) =>
        reject(new Error(err.errMsg || t('authPlugin.loginFailed', { provider: 'Apple' }))),
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error(t('authPlugin.unsupported', { provider: 'Apple' })))
  /* #endif */
}

/**
 * Google 登录
 * uni 无 App 端官方 API，需走 Web OAuth
 */
export function loginWithGoogle() {
  return Promise.reject(new Error(t('authPlugin.unsupported', { provider: 'Google' })))
}

/**
 * Facebook 登录
 * uni 无 App 端官方 API，需走 Web OAuth
 */
export function loginWithFacebook() {
  return Promise.reject(new Error(t('authPlugin.unsupported', { provider: 'Facebook' })))
}

/**
 * 检查是否已安装对应社交 App
 * uni 无官方能力，统一返回 false
 * @returns {Promise<Boolean>}
 */
export function isAppInstalled() {
  return Promise.resolve(false)
}

/**
 * 退出登录（uni.login 在原生侧无持续态，无需清理）
 */
export function logoutFromProvider() {
  // uni.login 不在客户端保留登录态，无需额外清理
}

/**
 * 社交登录调用封装（composable）
 * 仅 Apple 走官方 uni.login，其余 provider 明确降级
 * @returns {{ login: Function, logout: Function, isAuthorized: Function }}
 */
export function useAuthPlugin() {
  return {
    login: (provider) => {
      if (provider !== 'apple') {
        return Promise.resolve({
          success: false,
          error: t('authPlugin.unsupported', { provider }),
        })
      }
      return loginWithApple()
        .then((data) => ({ success: true, data }))
        .catch((error) => ({ success: false, error: error.message }))
    },
    logout: () => Promise.resolve({ success: true }),
    isAuthorized: () => Promise.resolve({ success: true, data: false }),
  }
}

export default {
  loginWithApple,
  loginWithGoogle,
  loginWithFacebook,
  isAppInstalled,
  logoutFromProvider,
  useAuthPlugin,
}
