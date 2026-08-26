import { config, REQUEST_TIMEOUT, RESPONSE_CODE } from './config'
import { getStorage, removeStorage, setStorage, STORAGE_KEYS } from './storage'

const pendingRequests = new Map()

function genRequestId() {
  return `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function getBearerToken() {
  return getStorage(STORAGE_KEYS.TOKEN)
}

function handleUnauthorized() {
  const refreshTokenVal = getStorage('moyuyo_refresh_token')
  if (refreshTokenVal) {
    import('@/api/user').then(({ refreshToken: doRefresh }) => {
      doRefresh(refreshTokenVal)
        .then((newTokens) => {
          setStorage(STORAGE_KEYS.TOKEN, newTokens.accessToken)
          if (newTokens.refreshToken) {
            setStorage('moyuyo_refresh_token', newTokens.refreshToken)
          }
        })
        .catch(() => {
          removeStorage(STORAGE_KEYS.TOKEN)
          removeStorage(STORAGE_KEYS.USER_INFO)
          removeStorage('moyuyo_refresh_token')
        })
    })
  } else {
    removeStorage(STORAGE_KEYS.TOKEN)
    removeStorage(STORAGE_KEYS.USER_INFO)
    uni.showToast({ title: 'Login expired, please login again', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/user/login' }), 1500)
  }
}

/**
 * 解析请求 base URL：
 * 1. 绝对路径直接用
 * 2. /api/v1/* 走 Vite dev proxy（dev）/ 同源相对路径（prod nginx 反代）
 * 3. 其它路径（向后兼容）拼接 config.apiBase（WordPress 默认）
 * 4. 显式注入 VITE_ADMIN_API_BASE 时，所有路径拼到该 base（移动端离线包场景）
 */
function resolveBaseUrl(url) {
  if (url.startsWith('http')) return url
  const absBase = (typeof process !== 'undefined' && process.env && process.env.VITE_ADMIN_API_BASE)
    || (typeof window !== 'undefined' && window.__MOYUYO_CONFIG__ && window.__MOYUYO_CONFIG__.VITE_ADMIN_API_BASE)
  if (absBase) return `${absBase}${url}`
  // dev 环境由 Vite proxy 转发 /api/v1/* 与 /uploads/*，用相对路径更稳
  // prod 环境通常 nginx 反代 /api/* 与 /uploads/*，同源相对路径同样有效
  if (url.startsWith('/api/v1/') || url.startsWith('/api/v1') || url.startsWith('/uploads')) {
    return url
  }
  return `${config.apiBase}${url}`
}

export function request(options) {
  const {
    url,
    method = 'GET',
    data = {},
    header = {},
    showLoading = false,
    showError = true,
    timeout = REQUEST_TIMEOUT,
    skipResultUnwrap = false,
  } = options

  const fullUrl = resolveBaseUrl(url)

  const reqHeader = {
    'Content-Type': 'application/json',
    ...header,
  }

  const token = getBearerToken()
  if (token) reqHeader.Authorization = `Bearer ${token}`

  const requestId = genRequestId()

  if (showLoading) uni.showLoading({ title: 'Loading...', mask: true })

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

        if (res.statusCode === RESPONSE_CODE.UNAUTHORIZED) {
          handleUnauthorized()
          reject(new Error('Unauthorized'))
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (skipResultUnwrap) {
            resolve(res.data)
          } else if (res.data && res.data.code === 0) {
            resolve(res.data.data)
          } else {
            const msg = res.data?.message || 'Request failed'
            if (showError) uni.showToast({ title: msg, icon: 'none' })
            reject(new Error(msg))
          }
        } else {
          const msg = res.data?.message || `Request failed (${res.statusCode})`
          if (showError) uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        pendingRequests.delete(requestId)
        if (showLoading) uni.hideLoading()
        const msg = err.errMsg?.includes('timeout') ? 'Request timeout' : 'Network error'
        if (showError) uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
    })

    pendingRequests.set(requestId, task)
  })
}

export const get = (url, params = {}, options = {}) => {
  const query = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = query ? `${url}${url.includes('?') ? '&' : '?'}${query}` : url
  return request({ ...options, url: fullUrl, method: 'GET' })
}

export const post = (url, data = {}, options = {}) =>
  request({ ...options, url, method: 'POST', data })

export const put = (url, data = {}, options = {}) =>
  request({ ...options, url, method: 'PUT', data })

export const del = (url, options = {}) => request({ ...options, url, method: 'DELETE' })