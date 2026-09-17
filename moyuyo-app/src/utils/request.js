import { config, REQUEST_TIMEOUT, RESPONSE_CODE } from './config'
import { getStorage, removeStorage, setStorage, STORAGE_KEYS } from './storage'
import { t } from '@/i18n'
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
 * 登录态失效提示:用 showModal 让用户主动确认再跳转,文案走 i18n。
 * 避免直接 reLaunch 造成"我在看账单突然掉到登录页"的体验割裂。
 */
function promptReLogin() {
  uni.showModal({
    title: t('common.sessionExpiredTitle'),
    content: t('common.sessionExpiredContent'),
    confirmText: t('common.relogin'),
    cancelText: t('common.later'),
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

// 后端中文错误 → i18n key 映射：后端 Result.message 多为中文字面量，无法跟随 App
// 语言切换。命中本表时按当前语言翻译，未收录的文案回落后端原文(运营/新增错误不受影响)。
// 新增后端错误提示时，在两份语言包的 serverMsg.* 里补文案并在此追加一条映射即可。
const SERVER_ERROR_MAP = {
  宠物不存在或无权访问: 'serverMsg.petNotFoundOrNoAccess',
  宠物不存在或无权操作: 'serverMsg.petNotFoundOrNoOperate',
  宠物不存在: 'serverMsg.petNotFound',
  记录不存在或不属于该宠物: 'serverMsg.recordNotBelongToPet',
  日记不存在或无权访问: 'serverMsg.diaryNotFoundOrNoAccess',
  日记不存在或无权操作: 'serverMsg.diaryNotFoundOrNoOperate',
  帖子不存在: 'serverMsg.postNotFound',
  关联宠物不存在或无权使用: 'serverMsg.linkedPetNotFound',
  '内容包含敏感词，无法发布': 'serverMsg.sensitiveWord',
}

/**
 * 后端结构化错误码前缀解析。
 * 一些后端错误需要带参数（如 "DELETION_HAS_ACTIVE_ORDERS:3"），
 * 前端解析前缀做 i18n key 匹配 + 模板插值，避免直接对整串字符串做精确匹配。
 *
 * 返回 { key, params }，未匹配返回 null。
 */
const STRUCTURED_ERROR_PATTERNS = [
  {
    // 注销申请被拒绝：账号存在未完成订单
    test: /^DELETION_HAS_ACTIVE_ORDERS:(\d+)$/,
    i18nKey: 'serverMsg.deletionHasActiveOrders',
    extract: (m) => ({ count: parseInt(m[1], 10) }),
  },
  {
    // 数据导出频次超限：下次可发起时间戳（毫秒）
    test: /^DATA_EXPORT_RATE_LIMITED:(\d+)$/,
    i18nKey: 'serverMsg.dataExportRateLimited',
    extract: (m) => ({ nextAllowedAtMillis: parseInt(m[1], 10) }),
  },
]

/**
 * 把 epoch 毫秒格式化为 "YYYY-MM-DD HH:mm" 本地时间字符串。
 * 用于限流提示"下次可发起时间"。
 */
function formatLocalDateTime(ms) {
  if (!ms || Number.isNaN(Number(ms))) return ''
  const d = new Date(Number(ms))
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}

function resolveStructuredError(msg) {
  const text = String(msg || '').trim()
  for (const p of STRUCTURED_ERROR_PATTERNS) {
    const m = text.match(p.test)
    if (m) {
      const params = p.extract(m)
      // 时间戳字段额外格式化为可读日期字符串
      if (params && typeof params.nextAllowedAtMillis === 'number') {
        params.date = formatLocalDateTime(params.nextAllowedAtMillis)
      }
      return { key: p.i18nKey, params }
    }
  }
  return null
}

// 后端错误提示本地化：命中映射返回当前语言文案，否则原样返回
function localizeServerMessage(msg) {
  if (!msg) return msg
  const text = String(msg).trim()
  // 1) 优先匹配结构化错误码（带参数的模板）
  const structured = resolveStructuredError(text)
  if (structured) {
    return t(structured.key, structured.params)
  }
  // 2) 命中静态文案表
  const key = SERVER_ERROR_MAP[text]
  return key ? t(key) : msg
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

  if (showLoading) uni.showLoading({ title: t('common.loading'), mask: true })

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
            // 后端 Result.error:code !== 0 但 HTTP 200,直接用 message(先做 i18n 本地化)
            const localized = localizeServerMessage(res.data?.message)
            const msg = localized || t('common.requestFailed', { status: res.statusCode })
            console.warn('[request] biz error:', fullUrl, res.statusCode, res.data)
            if (showError) uni.showToast({ title: msg, icon: 'none', duration: 3000 })
            reject(new Error(msg))
          }
        } else {
          // HTTP 4xx/5xx:后端通常也返回 Result.error JSON(code+message)
          // 命中后端错误映射表时按当前语言翻译,未收录则回落后端原文
          const backendMsg = res.data?.message
          const status = res.statusCode
          const msg =
            (localizeServerMessage(backendMsg) || '').trim() ||
            t('common.requestFailedHttp', { status })
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
        const msg = err.errMsg?.includes('timeout')
          ? t('common.requestTimeout')
          : t('common.networkError')
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

// request() 解构出来的 options 字段白名单;不在此列表的 key 视为 query params
const REQUEST_OPTION_KEYS = new Set([
  'method',
  'data',
  'header',
  'showLoading',
  'showError',
  'timeout',
  'skipResultUnwrap',
  'hideErrorToast',
])

export const del = (url, params = {}, options = {}) => {
  // del 第二参数统一为 params(query string),与 get/post 签名保持一致
  // 第三个参数为 options(headers/timeout 等);旧代码兼容:
  //   - del(url, options) → params 取空,options 正常展开(原行为)
  // 判断 params 是否实际是 options:含 request 白名单任一字段即视为 options
  if (params && !Array.isArray(params) && typeof params === 'object') {
    const looksLikeOptions = Object.keys(params).some((k) => REQUEST_OPTION_KEYS.has(k))
    if (looksLikeOptions) {
      options = params
      params = {}
    }
  }
  const query = Object.keys(params || {})
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  const fullUrl = query ? `${url}${url.includes('?') ? '&' : '?'}${query}` : url
  return request({ ...options, url: fullUrl, method: 'DELETE' })
}
