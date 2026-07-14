/**
 * 用户 API
 * - 注册：/wc/v3/customers（WooCommerce 顾客注册）
 * - 登录：/api/v1/token（WP OAuth Server Password Grant）
 * - 用户信息：/api/v1/userinfo（OAuth 用户信息端点）
 * - Token 刷新：/api/v1/token（refresh_token grant）
 * - 社交登录：通过 OAuth Authorization Code 流程（WebView）
 *
 * WP OAuth Server（MiniOrange）安装指南：
 * 1. WP 后台 → 插件 → 安装 "REST API Authentication" 或 "WP OAuth Server"
 * 2. 启用 OAuth 认证方法
 * 3. WP OAuth Server → Client → 添加客户端 → 获取 Client ID/Secret
 * 4. 在 .env 中配置 VITE_OAUTH_CLIENT_ID
 */
import { get, post, put } from '@/utils/request'
import { config } from '@/utils/config'
import { removeStorage, STORAGE_KEYS, setStorage } from '@/utils/storage'

const CUSTOMER_BASE = '/wc/v3/customers'

/**
 * 用户注册（WooCommerce 顾客）
 */
export function register(userData) {
  return post(CUSTOMER_BASE, userData, { useAuth: true, useOAuth: false })
}

/**
 * OAuth Password Grant 登录
 * POST /api/v1/token
 * Content-Type: application/x-www-form-urlencoded
 *
 * @param {String} username 邮箱或用户名
 * @param {String} password 密码
 * @returns {Promise<{access_token, refresh_token, expires_in}>}
 */
export function login(username, password) {
  return new Promise((resolve, reject) => {
    // Basic Auth 头（client_id:client_secret 做 Base64）
    const authStr = `${config.oauthClientId}:${config.oauthClientSecret}`
    const authBase64 = btoa(authStr)
    const header = {
      'Authorization': `Basic ${authBase64}`,
      'Content-Type': 'application/x-www-form-urlencoded'
    }

    const body = `grant_type=password&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&client_id=${config.oauthClientId}`

    uni.showLoading({ title: '登录中...', mask: true })

    uni.request({
      url: `${config.apiBase}${config.oauthTokenEndpoint}`,
      method: 'POST',
      header,
      data: body,
      success: (res) => {
        uni.hideLoading()
        if (res.statusCode === 200 && res.data?.access_token) {
          // 存储 refresh_token 备用
          if (res.data.refresh_token) {
            setStorage('moyuyo_refresh_token', res.data.refresh_token)
          }
          resolve(res.data)
        } else {
          const msg = res.data?.error_description || res.data?.message || '登录失败'
          uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        uni.hideLoading()
        const msg = '网络异常，请重试'
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      }
    })
  })
}

/**
 * 刷新 Token
 * POST /api/v1/token (grant_type=refresh_token)
 */
export function refreshToken(refreshToken) {
  return new Promise((resolve, reject) => {
    const auth = btoa(unescape(encodeURIComponent(`${config.oauthClientId}:${config.oauthClientSecret}`)))
    const body = `grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}&client_id=${config.oauthClientId}`

    uni.request({
      url: `${config.apiBase}${config.oauthRefreshEndpoint}`,
      method: 'POST',
      header: {
        'Authorization': `Basic ${auth}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      data: body,
      success: (res) => {
        if (res.statusCode === 200 && res.data?.access_token) {
          if (res.data.refresh_token) {
            setStorage('moyuyo_refresh_token', res.data.refresh_token)
          }
          resolve(res.data)
        } else {
          reject(new Error('Token refresh failed'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || 'Token refresh failed'))
    })
  })
}

/**
 * 获取当前用户信息
 * GET /api/v1/userinfo
 * 需要在 WP OAuth Server 中配置 scope=openid profile email
 */
export function getUserInfo() {
  return get(config.oauthUserInfoEndpoint, {}, { useOAuth: true, useAuth: false, showLoading: false })
}

/**
 * 验证 Token 是否有效
 * 通过调用 userinfo 端点来间接验证
 */
export function validateToken() {
  return getUserInfo()
}

/**
 * 获取 WooCommerce 顾客信息（用于 billing/shipping 地址等）
 */
export function getCustomerInfo(customerId) {
  return get(`${CUSTOMER_BASE}/${customerId}`, {}, { useAuth: true, useOAuth: false })
}

/**
 * 更新用户资料（WooCommerce 顾客）
 */
export function updateUser(customerId, data) {
  return put(`${CUSTOMER_BASE}/${customerId}`, data, { useAuth: true, useOAuth: false })
}

/**
 * 退出登录：清除本地 Token
 */
export function logout() {
  removeStorage(STORAGE_KEYS.TOKEN)
  removeStorage(STORAGE_KEYS.USER_INFO)
  removeStorage('moyuyo_refresh_token')
  return Promise.resolve(true)
}

/**
 * 忘记密码 - 触发重置邮件
 * 依赖 WP 端自定义端点或 wp-login.php?action=lostpassword
 */
export function forgotPassword(email) {
  return post('/wp/v2/users/lostpassword', { user_login: email }, { useAuth: false, useOAuth: false })
}

/**
 * 社交登录（OAuth Authorization Code 流程）
 * 跳转 WP OAuth Server 授权页进行社交登录（Apple/Google/Facebook）
 * @param {String} provider apple | google | facebook
 * @returns {String} 授权页 URL
 */
export function getSocialLoginUrl(provider) {
  const redirectUri = `${config.wpBase}/oauth-callback`
  const scope = 'openid profile email'
  // 社交登录通过 Super Socializer + WP OAuth Server 配合实现
  // WP OAuth Server 的 authorize 端点处理社交登录重定向
  return `${config.wpBase}/wp-json/moserver/authorize?` +
    `response_type=code&` +
    `client_id=${config.oauthClientId}&` +
    `redirect_uri=${encodeURIComponent(redirectUri)}&` +
    `scope=${encodeURIComponent(scope)}&` +
    `provider=${provider}`
}

export default {
  register,
  login,
  refreshToken,
  getUserInfo,
  validateToken,
  getCustomerInfo,
  updateUser,
  logout,
  forgotPassword,
  getSocialLoginUrl
}

/**
 * 工具：btoa Base64 编码兼容处理
 */
function btoa(str) {
  if (typeof globalThis !== 'undefined' && typeof globalThis.btoa === 'function') {
    return globalThis.btoa(str)
  }
  // uni-app 环境下不使用 Node Buffer
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
  let output = ''
  let i = 0
  while (i < str.length) {
    const c1 = str.charCodeAt(i++)
    const c2 = str.charCodeAt(i++) || 0
    const c3 = str.charCodeAt(i++) || 0
    const e1 = c1 >> 2
    const e2 = ((c1 & 3) << 4) | (c2 >> 4)
    const e3 = ((c2 & 15) << 2) | (c3 >> 6)
    const e4 = c3 & 63
    output += chars.charAt(e1) + chars.charAt(e2) + chars.charAt(isNaN(c2) ? 64 : e3) + chars.charAt(isNaN(c3) ? 64 : e4)
  }
  return output
}
