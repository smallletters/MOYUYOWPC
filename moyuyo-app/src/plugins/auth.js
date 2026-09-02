/**
 * 社交登录原生插件调用封装
 * 插件标识: MOYUYO-Auth
 * 功能: Apple Sign-In / Google Sign-In / Facebook Login
 */
/* eslint-disable no-unreachable */

const PLUGIN_NAME = 'MOYUYO-Auth'

/**
 * Apple 登录
 * @returns {Promise<Object>} { userId, fullName, email, identityToken }
 */
export function loginWithApple() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  if (!plugin) {
    return Promise.reject(new Error('原生登录插件不可用,请使用其他登录方式'))
  }
  return new Promise((resolve, reject) => {
    plugin.loginWithApple({}, (result) => {
      if (result.success) {
        resolve({
          userId: result.userId,
          fullName: result.fullName,
          email: result.email,
          identityToken: result.identityToken,
        })
      } else {
        reject(new Error(result.message || 'Apple 登录失败'))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持 Apple 登录'))
  /* #endif */
}

/**
 * Google 登录
 * @returns {Promise<Object>} { idToken, displayName, email, photoUrl }
 */
export function loginWithGoogle() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  if (!plugin) {
    return Promise.reject(new Error('原生登录插件不可用,请使用其他登录方式'))
  }
  return new Promise((resolve, reject) => {
    plugin.loginWithGoogle({}, (result) => {
      if (result.success) {
        resolve({
          idToken: result.idToken,
          displayName: result.displayName,
          email: result.email,
          photoUrl: result.photoUrl,
        })
      } else {
        reject(new Error(result.message || 'Google 登录失败'))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持 Google 登录'))
  /* #endif */
}

/**
 * Facebook 登录
 * @returns {Promise<Object>} { accessToken, userId, displayName, email }
 */
export function loginWithFacebook() {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  if (!plugin) {
    return Promise.reject(new Error('原生登录插件不可用,请使用其他登录方式'))
  }
  return new Promise((resolve, reject) => {
    plugin.loginWithFacebook({}, (result) => {
      if (result.success) {
        resolve({
          accessToken: result.accessToken,
          userId: result.userId,
          displayName: result.displayName,
          email: result.email,
        })
      } else {
        reject(new Error(result.message || 'Facebook 登录失败'))
      }
    })
  })
  // #endif
  /* #ifndef APP-PLUS */
  return Promise.reject(new Error('当前环境不支持 Facebook 登录'))
  /* #endif */
}

/**
 * 检查当前设备是否已安装对应社交 App
 * @param {String} provider apple | google | facebook
 * @returns {Promise<Boolean>}
 */
export function isAppInstalled(provider) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  if (!plugin) return Promise.resolve(false)
  return new Promise((resolve) => {
    plugin.isAppInstalled({ provider }, (result) => {
      resolve(!!result.installed)
    })
  })
  // #endif
  return Promise.resolve(false)
}

/**
 * 退出登录（清除原生侧登录状态）
 */
export function logoutFromProvider(provider) {
  // #ifdef APP-PLUS
  const plugin = uni.requireNativePlugin(PLUGIN_NAME)
  if (!plugin) return
  plugin.logout({ provider }, () => {})
  // #endif
}

/**
 * 社交登录插件调用封装（composable）
 * 与 auth.ts 中 useAuthPlugin 保持一致
 * @returns {{ login: Function, logout: Function, isAuthorized: Function }}
 */
export function useAuthPlugin() {
  const call = (method, args) => {
    return new Promise((resolve) => {
      // #ifdef APP-PLUS
      const plugin = uni.requireNativePlugin(PLUGIN_NAME)
      if (!plugin) {
        resolve({ success: false, error: '原生登录插件不可用' })
        return
      }
      plugin[method](args, (res) => resolve(res))
      // #endif
      // #ifndef APP-PLUS
      resolve({ success: false, error: '原生登录插件不可用' })
      // #endif
    })
  }

  return {
    login: (provider) => call('login', { provider }),
    logout: (provider) => call('logout', { provider }),
    isAuthorized: (provider) => call('isAuthorized', { provider }),
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
