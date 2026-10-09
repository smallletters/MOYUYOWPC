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

// 401 刷新去抖锁：并发请求共用一次 refresh，避免 5+ 个接口同时触发 5+ 个 modal
let isRefreshing = false
// 待重试的 401 请求队列：refresh 成功后按入队顺序逐个重发原请求
// 每项包含 { options, resolve, reject }，resolve 接收重试结果，reject 接收失败原因
const pendingUnauthorizedHandlers = []

// 登录过期 modal 防重入锁：
//   页面 onLoad + onShow 一进入就会发 2~4 个并发请求，无 refresh token 时
//   都会触发 promptReLogin，没这把锁就会连续弹出 4 次"登录已过期"。
//   用户关闭 modal（success 回调）后解锁,允许下次真过期时再提示。
let isExpiredModalShown = false

/**
 * 401 处理核心：先用 refreshToken 换新 accessToken，再用新 token 自动重发原请求
 * <p>
 * 设计要点：
 * 1) refresh 成功：用新 token 串行重发"当前 options + 队列里的所有 401 请求"，
 *    caller 通过 Promise.then 直接接住当前 options 的重发结果（成功/失败都正确传播）
 * 2) isRefreshing 在"所有重发都完成"之后才释放，避免重发期间新 401 又触发一次 refresh
 * 3) 错误分类处理：
 *    - refresh 失败：清凭证 + 弹"登录已过期"modal + reject caller('Unauthorized')
 *    - 首项重发业务错误（4xx/5xx/network）：原错误透传给 caller，token 仍有效
 * <p>
 * 历史实现只 resolve 队列里的 promise（其实队列在首 401 时还是空的），原业务请求
 * 永远被 reject('Unauthorized')，需要用户手动重试或重新登录。修复后 401 对用户完全
 * 透明：refresh 成功后会自动重发原请求并把结果 resolve 回去，refresh 失败才弹 modal。
 *
 * @param {object} options 触发 401 的原请求 options（用于重发）
 * @returns {Promise} resolve = 重发结果；reject = 刷新失败或重发失败
 */
function handleUnauthorized(options) {
  const refreshTokenVal = safeGet('moyuyo_refresh_token')
  if (!refreshTokenVal) {
    // 无 refresh token：清凭证 + 弹窗确认再跳转
    safeRemove(STORAGE_KEYS.TOKEN)
    safeRemove(STORAGE_KEYS.USER_INFO)
    promptReLogin()
    return Promise.reject(new Error('Unauthorized'))
  }
  if (isRefreshing) {
    // 正在刷新中：把原请求入队，刷新成功后按顺序重发
    return new Promise((resolve, reject) => {
      pendingUnauthorizedHandlers.push({ options, resolve, reject })
    })
  }
  isRefreshing = true
  // 关键：把"当前 options 的重发"和"队列重发"放在同一个链路里：
  // 1) 先发当前 options 拿到结果
  // 2) 再按顺序处理队列
  // 3) 全部完成后才释放 isRefreshing
  // 任何一步失败都不会让 caller 拿到 undefined（首项重发失败直接 reject 当前 caller）
  // 用 .catch 区分错误来源：refresh 失败 vs 首项业务错误，决定是否清凭证+弹 modal
  return refreshToken(refreshTokenVal)
    .catch((refreshErr) => {
      // refresh 失败：标记 _isRefreshFailure 让外层 .catch 走"清凭证+弹 modal"分支
      // 然后把 refresh 错误转成一个标记对象传出去
      const err = new Error('Unauthorized')
      err._isRefreshFailure = true
      err._originalError = refreshErr
      throw err
    })
    .then(async (newTokens) => {
      safeSet(STORAGE_KEYS.TOKEN, newTokens.accessToken)
      if (newTokens.refreshToken) {
        safeSet('moyuyo_refresh_token', newTokens.refreshToken)
      }
      // 先发当前 options（caller 期望的结果），失败直接抛出进 catch
      let currentResult
      try {
        currentResult = await request({ ...options, _isRetry: true })
      } catch (e) {
        // 首项重发失败：先把队列里所有 caller reject 掉，再把错误抛给当前 caller
        // 关键：首项业务错误透传给 caller，token 仍有效，不要清凭证/弹 modal
        const queue = pendingUnauthorizedHandlers.slice()
        pendingUnauthorizedHandlers.length = 0
        queue.forEach(({ reject }) => reject(new Error('Unauthorized')))
        throw e
      }
      // 首项成功：处理队列里的剩余 401 请求
      const queue = pendingUnauthorizedHandlers.slice()
      pendingUnauthorizedHandlers.length = 0
      for (const h of queue) {
        try {
          const r = await request({ ...h.options, _isRetry: true })
          h.resolve(r)
        } catch (e) {
          h.reject(e)
        }
      }
      return currentResult
    })
    .catch((err) => {
      // 关键：只有 refresh 自身失败（_isRefreshFailure=true）才清凭证+弹 modal
      // 首项重发的业务错误（4xx/5xx/network）透传给 caller，token 仍有效
      if (err && err._isRefreshFailure) {
        safeRemove(STORAGE_KEYS.TOKEN)
        safeRemove(STORAGE_KEYS.USER_INFO)
        safeRemove('moyuyo_refresh_token')
        const queue = pendingUnauthorizedHandlers.slice()
        pendingUnauthorizedHandlers.length = 0
        queue.forEach(({ reject }) => reject(new Error('Unauthorized')))
        promptReLogin()
        throw new Error('Unauthorized')
      }
      // 首项业务错误：原样透传给 caller（token 仍有效，只是请求本身有 4xx/5xx 或网络问题）
      throw err
    })
    .finally(() => {
      // 关键：放在 .finally 里确保重发链路全部完成后才释放 isRefreshing
      // 避免重发期间新 401 又触发一次 refresh
      isRefreshing = false
    })
}

/**
 * 登录态失效提示:用 showModal 让用户主动确认再跳转,文案走 i18n。
 * 避免直接 reLaunch 造成"我在看账单突然掉到登录页"的体验割裂。
 * 加 isExpiredModalShown 防重入:并发 401 只弹一次,用户关闭(点稍后/确认)后再允许下次弹。
 */
function promptReLogin() {
  if (isExpiredModalShown) return
  isExpiredModalShown = true
  // 关键：先 hideToast 避免上一个 toast（"网络异常"等）覆盖掉 showModal
  // APP / 小程序端 showToast 与 showModal 同时存在时 toast 会遮挡 modal
  // 导致"登录已过期"看起来没弹出来
  uni.hideToast()
  uni.showModal({
    title: t('common.sessionExpiredTitle'),
    content: t('common.sessionExpiredContent'),
    confirmText: t('common.relogin'),
    cancelText: t('common.later'),
    success: (res) => {
      isExpiredModalShown = false
      if (res.confirm) {
        uni.reLaunch({ url: '/pages/user/login' })
      }
    },
    fail: () => {
      // showModal 自身失败也要解锁,否则后续真过期也弹不出来
      isExpiredModalShown = false
    },
  })
}

/**
 * 主动触发"登录已过期"提示
 * <p>
 * 暴露给 App.vue / store 等需要在"非业务请求触发的 401"场景下弹 modal：
 * 典型场景是 App.vue onShow 探活时发现 refresh token 也失效，调用 forceLogout 之后
 * 用户无感知（被悄悄踢出登录），需要主动弹 modal 告知原因。
 * <p>
 * 复用 promptReLogin 的防重入锁，避免与 401 流程弹的 modal 重复。
 */
export function triggerSessionExpired() {
  promptReLogin()
}

/**
 * 探测当前网络是否可用（uni.getNetworkType 异步回调）
 * <p>
 * 用于 fail 分支：30 分钟后台切回时 OS 网络栈可能 keep-alive 失效导致 uni.request fail，
 * 但实际网络是通的。探测一次可避免误报"网络异常"。
 *
 * @returns {Promise<boolean>} true = 网络可用；false = 无网络
 */
function probeNetwork() {
  return new Promise((resolve) => {
    uni.getNetworkType({
      success: (res) => {
        // networkType: wifi / 2g / 3g / 4g / 5g / ethernet / none / unknown
        resolve(res.networkType && res.networkType !== 'none')
      },
      fail: () => resolve(false),
    })
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
  // AddressServiceImpl 校验失败
  'Country is required': 'serverMsg.addressCountryRequired',
  'Address not found': 'serverMsg.addressNotFound',
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
  // _isRetry 标记：refresh 后重发请求时为 true，避免新 token 仍 401 时再次进入 refresh 死循环
  const isRetry = options._isRetry === true
  // _silent 标记：内部调用（如 refreshToken）失败时静默处理，不弹 toast、不标记 isNetworkError
  // 因为 refresh 失败时外层 handleUnauthorized 会自己弹"登录已过期"modal，
  // 如果 fail 分支再弹"网络异常"会同时出现两个提示互相干扰
  const silent = options._silent === true
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

        // 401 处理：refresh 成功后会自动重发原请求并 resolve，无需业务方感知
        // _isRetry=true 时直接拒绝（避免 refresh 后仍 401 时死循环）
        // _silent=true 时直接拒绝（refresh 内部调用不能再走 401 自动重试，避免死循环）
        if (res.statusCode === RESPONSE_CODE.UNAUTHORIZED && !isRetry && !silent) {
          handleUnauthorized(options)
            .then((retryResult) => resolve(retryResult))
            .catch(() => reject(new Error('Unauthorized')))
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
        } else if (silent) {
          // silent 模式（内部 refreshToken 调用）：4xx/5xx 也不弹 toast，直接 reject 让外层处理
          // 避免 refresh 失败时同时弹"后端错误" + "登录已过期"两个提示
          const err = new Error(`HTTP ${res.statusCode} (silent): ${res.data?.message || ''}`)
          err.statusCode = res.statusCode
          err.body = res.data
          err.url = fullUrl
          err._silent = true
          reject(err)
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
        // silent 模式（内部 refreshToken 调用）：不弹 toast、不重试、不标记 isNetworkError
        // 外层 handleUnauthorized 会自己处理错误提示
        if (silent) {
          console.warn('[request] silent fail:', fullUrl, err)
          reject(new Error(`Network fail (silent): ${err.errMsg || 'unknown'}`))
          return
        }
        // 30 分钟后台切回：OS 网络栈 keep-alive 失效会导致 uni.request fail（timeout / socket reset），
        // 但实际网络是通的。探测一次网络状态：若可用则重试一次原请求，否则才报"网络异常"
        // _isRetry=true 时不再重试（避免失败请求无限重试）
        if (!isRetry) {
          probeNetwork().then((hasNetwork) => {
            if (hasNetwork) {
              console.warn('[request] fail but network ok, retry once:', fullUrl, err)
              // 隐式重试：不再弹 toast（让最终结果决定是否提示）
              request({ ...options, _isRetry: true, showError })
                .then(resolve)
                .catch((retryErr) => {
                  // 重试仍失败：按 fail 原逻辑提示
                  handleNetworkFailToast(retryErr.errMsg, fullUrl, showError)
                  reject(retryErr)
                })
              return
            }
            // 真的无网络：走原 fail 提示
            handleNetworkFailToast(err.errMsg, fullUrl, showError)
            const e = new Error(t('common.networkError'))
            e.isNetworkError = true
            e.url = fullUrl
            reject(e)
          })
          return
        }
        // 重试仍 fail：按原逻辑提示
        handleNetworkFailToast(err.errMsg, fullUrl, showError)
        const e = new Error(t('common.networkError'))
        e.isNetworkError = true
        e.url = fullUrl
        reject(e)
      },
    })

    pendingRequests.set(requestId, task)
  })
}

/**
 * 网络失败的 toast 提示（从 fail 分支抽出来复用）
 * <p>
 * 区分 timeout 与其他网络错误：timeout 通常是后台 keep-alive 失效，给"请求超时"提示更友好
 */
function handleNetworkFailToast(errMsg, fullUrl, showError) {
  const isTimeout = errMsg?.includes('timeout')
  const msg = isTimeout ? t('common.requestTimeout') : t('common.networkError')
  console.warn('[request] network fail:', fullUrl, errMsg)
  if (showError) {
    // 先 hide 避免与 modal 冲突
    uni.hideToast()
    uni.showToast({ title: msg, icon: 'none' })
  }
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
