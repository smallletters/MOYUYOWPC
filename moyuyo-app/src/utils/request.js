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

  const fullUrl = url.startsWith('http') ? url : `${config.apiBase}${url}`

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
            // 与后端 Result.CODE_SUCCESS 对齐：业务成功码为 0（非 HTTP 200）
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
