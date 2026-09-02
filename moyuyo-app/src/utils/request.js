import { config, REQUEST_TIMEOUT, RESPONSE_CODE } from './config'
import { getStorage, removeStorage, setStorage, STORAGE_KEYS } from './storage'
// 静态 import(替代原 import('@/api/user') 动态 import):
//   HBuilder(uni-app 3.8.12)在编译期看到 dynamic import 会启用 Rollup
//   代码分割,但其 output.format 默认 iife,二者冲突 → build failed.
import { refreshToken } from '@/api/user'

const pendingRequests = new Map()

function genRequestId() {
  return `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

/**
 * 安全的 storage 读取:storage 抛异常(被注入异常 key、底层崩溃)
 * 时返回空值,不让 401 处理链路整体崩溃。
 */
function safeGet(key) {
  try {
    return getStorage(key)
  } catch (e) {
    console.warn('[request] safeGet failed for key:', key, e)
    return ''
  }
}

function safeRemove(key) {
  try {
    removeStorage(key)
  } catch (e) {
    console.warn('[request] safeRemove failed for key:', key, e)
  }
}

function safeSet(key, value) {
  try {
    setStorage(key, value)
  } catch (e) {
    console.warn('[request] safeSet failed for key:', key, e)
  }
}

function getBearerToken() {
  return safeGet(STORAGE_KEYS.TOKEN)
}

function handleUnauthorized() {
  const refreshTokenVal = safeGet('moyuyo_refresh_token')
  if (refreshTokenVal) {
    refreshToken(refreshTokenVal)
      .then((newTokens) => {
        safeSet(STORAGE_KEYS.TOKEN, newTokens.accessToken)
        if (newTokens.refreshToken) {
          safeSet('moyuyo_refresh_token', newTokens.refreshToken)
        }
      })
      .catch(() => {
        // 刷新失败:清空凭证 + 文案中文化 + 弹窗确认再跳转,避免突兀踢出
        safeRemove(STORAGE_KEYS.TOKEN)
        safeRemove(STORAGE_KEYS.USER_INFO)
        safeRemove('moyuyo_refresh_token')
        promptReLogin()
      })
  } else {
    // 无 refresh token:同样先弹窗确认,避免用户在查看账单时被强制踢出
    safeRemove(STORAGE_KEYS.TOKEN)
    safeRemove(STORAGE_KEYS.USER_INFO)
    promptReLogin()
  }
}

/**
 * 登录态失效提示:用 showModal 让用户主动确认再跳转,文案中文化。
 * 避免直接 reLaunch 造成"我在看账单突然掉到登录页"的体验割裂。
 */
function promptReLogin() {
  uni.showModal({
    title: '登录已过期',
    content: '您的登录状态已失效,重新登录后将返回此页面',
    confirmText: '重新登录',
    cancelText: '稍后',
    success: (res) => {
      if (res.confirm) {
        uni.reLaunch({ url: '/pages/user/login' })
      }
    },
  })
}

/**
 * 解析请求 base URL：
 * 1. 绝对路径直接用
 * 2. /api/v1/* 走 Vite dev proxy（dev）/ 同源相对路径（prod nginx 反代）
 * 3. 其它路径（向后兼容）拼接 config.apiBase（WordPress 默认）
 * 4. 显式注入 VITE_ADMIN_API_BASE 时，所有路径拼到该 base（移动端离线包场景）
 * 注意：vite.config.js 的 define 会把 process.env.VITE_ADMIN_API_BASE 在编译期
 *      静态替换成字符串字面量；不能用 typeof process !== 'undefined' 守卫
 *      （uni-app APP 端 process 状态不可靠），也不能用 import.meta.env
 *      （vite-plugin-uni APP 端 polyfill 会用到 new URL/document 导致白屏）。
 *      直接读 process.env.VITE_ADMIN_API_BASE 即可。
 */
function resolveBaseUrl(url) {
  if (url.startsWith('http')) return url
  const absBase = process.env.VITE_ADMIN_API_BASE
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
            // 后端 Result.error:code !== 0 但 HTTP 200,直接用 message
            const msg = res.data?.message || `请求失败(${res.statusCode})`
            console.warn('[request] biz error:', fullUrl, res.statusCode, res.data)
            if (showError) uni.showToast({ title: msg, icon: 'none', duration: 3000 })
            reject(new Error(msg))
          }
        } else {
          // HTTP 4xx/5xx:后端通常也返回 Result.error JSON(code+message)
          const backendMsg = res.data?.message
          const status = res.statusCode
          const msg = (backendMsg && String(backendMsg).trim()) || `Request failed (${status})`
          console.error('[request] http error:', fullUrl, status, res.data)
          // 把 HTTP 状态码附加到 Error 对象,便于登录页区分 401/403/400 等
          const err = new Error(msg)
          err.statusCode = status
          err.body = res.data
          err.url = fullUrl
          if (showError) uni.showToast({ title: msg, icon: 'none', duration: 3000 })
          reject(err)
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
