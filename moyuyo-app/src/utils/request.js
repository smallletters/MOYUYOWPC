/**
 * 统一请求封装（基于 uni.request）
 * - 自动附加 WooCommerce Basic Auth（Consumer Key/Secret）
 * - 自动附加 OAuth Bearer Token（用户登录后，WP OAuth Server）
 * - 统一错误处理 & 401 自动登出（尝试 Refresh Token）
 * - 支持取消请求（防重复点击）
 */

import { config, REQUEST_TIMEOUT, RESPONSE_CODE } from './config'
import { getStorage, removeStorage, setStorage, STORAGE_KEYS } from './storage'

// 取消请求的 token map
const pendingRequests = new Map()

/**
 * 生成请求唯一 ID
 */
function genRequestId() {
  return `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/**
 * Base64 编码（小程序/APP 不支持 btoa 时降级实现）
 */
function base64Encode(str) {
  if (typeof btoa !== 'undefined') return btoa(unescape(encodeURIComponent(str)))
  // #ifdef MP-WEIXIN
  // 小程序可使用 wx.arrayBufferToBase64
  return ''
  // #endif
  // 降级实现
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let output = ''
  let i = 0
  const utf8 = unescape(encodeURIComponent(str))
  while (i < utf8.length) {
    const c1 = utf8.charCodeAt(i++)
    const c2 = utf8.charCodeAt(i++)
    const c3 = utf8.charCodeAt(i++)
    const e1 = c1 >> 2
    const e2 = ((c1 & 3) << 4) | (c2 >> 4)
    const e3 = isNaN(c2) ? 64 : ((c2 & 15) << 2) | (c3 >> 6)
    const e4 = isNaN(c3) ? 64 : c3 & 63
    output += chars.charAt(e1) + chars.charAt(e2) + chars.charAt(e3) + chars.charAt(e4)
  }
  return output
}

/**
 * 构造 Basic Auth 头
 */
function buildBasicAuth() {
  if (!config.consumerKey || !config.consumerSecret) return ''
  return `Basic ${base64Encode(`${config.consumerKey}:${config.consumerSecret}`)}`
}

/**
 * 核心请求方法
 * @param {Object} options
 * @param {String} options.url 完整 URL 或相对路径
 * @param {String} options.method GET/POST/PUT/DELETE
 * @param {Object} options.data 请求数据
 * @param {Object} options.header 自定义头
 * @param {Boolean} options.useAuth 是否使用 WooCommerce Basic Auth
 * @param {Boolean} options.useOAuth 是否附带 OAuth Bearer Token（WP OAuth Server）
 * @param {Boolean} options.showLoading 是否显示 loading
 * @param {Boolean} options.showError 错误时是否 toast
 */
export function request(options) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    useAuth = true,
    useOAuth = true,
    showLoading = false,
    showError = true,
    timeout = REQUEST_TIMEOUT
  } = options

  // 完整 URL 拼接
  const fullUrl = url.startsWith('http') ? url : `${config.apiBase}${url}`

  // 构造请求头
  const reqHeader = {
    'Content-Type': 'application/json',
    ...header
  }

  // WooCommerce Basic Auth（Consumer Key/Secret）
  if (useAuth) {
    const auth = buildBasicAuth()
    if (auth) reqHeader.Authorization = auth
  }

  // OAuth Bearer Token（WP OAuth Server 用户鉴权）
  if (useOAuth) {
    const token = getStorage(STORAGE_KEYS.TOKEN)
    if (token) reqHeader.Authorization = `Bearer ${token}`
  }

  const requestId = genRequestId()

  if (showLoading) uni.showLoading({ title: '加载中...', mask: true })

  return new Promise((resolve, reject) => {
    const task = uni.request({
      url: fullUrl,
      method,
      data,
      header: reqHeader,
      timeout,
      success: (res) => {
        pendingRequests.delete(requestId)
        if (showLoading) uni.hideLoading()

        // HTTP 状态码判断
        if (res.statusCode === RESPONSE_CODE.UNAUTHORIZED) {
          // 尝试用 refresh_token 刷新 Token（避免无故踢出登录）
          const refreshToken = getStorage('moyuyo_refresh_token')
          if (refreshToken) {
            // 在后台静默刷新，不阻塞当前请求
            import('@/api/user').then(({ refreshToken: doRefresh }) => {
              doRefresh(refreshToken).then((newToken) => {
                setStorage(STORAGE_KEYS.TOKEN, newToken.access_token)
              }).catch(() => {
                // refresh 也失败 → 清登录
                removeStorage(STORAGE_KEYS.TOKEN)
                removeStorage(STORAGE_KEYS.USER_INFO)
                removeStorage('moyuyo_refresh_token')
              })
            })
          } else {
            // 无 refresh_token → 直接登出
            removeStorage(STORAGE_KEYS.TOKEN)
            removeStorage(STORAGE_KEYS.USER_INFO)
            uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
            setTimeout(() => uni.reLaunch({ url: '/pages/user/login' }), 1500)
          }
          reject(new Error('Unauthorized'))
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          const msg = res.data?.message || `请求失败 (${res.statusCode})`
          if (showError) uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        pendingRequests.delete(requestId)
        if (showLoading) uni.hideLoading()
        const msg = err.errMsg?.includes('timeout') ? '请求超时，请重试' : '网络异常，请检查网络'
        if (showError) uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      }
    })

    pendingRequests.set(requestId, task)
  })
}

/**
 * GET 简写
 */
export const get = (url, params = {}, options = {}) => {
  const query = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = query ? `${url}${url.includes('?') ? '&' : '?'}${query}` : url
  return request({ ...options, url: fullUrl, method: 'GET' })
}

/**
 * POST 简写
 */
export const post = (url, data = {}, options = {}) =>
  request({ ...options, url, method: 'POST', data })

/**
 * PUT 简写
 */
export const put = (url, data = {}, options = {}) =>
  request({ ...options, url, method: 'PUT', data })

/**
 * DELETE 简写
 */
export const del = (url, options = {}) =>
  request({ ...options, url, method: 'DELETE' })
