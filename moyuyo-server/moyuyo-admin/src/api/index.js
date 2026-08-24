import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 防止多个并发 401 请求触发多次登录跳转
let isRedirectingToLogin = false

// 后端 API 根地址：dev 默认 '/api'（Vite 代理），prod 由 VITE_API_BASE_URL 注入完整后端地址
// 注意：axios 与组件（el-upload 等）共用此值，避免出现"axios 用 /api 但 el-upload 用了别的"的不一致
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const api = axios.create({
  baseURL: `${API_BASE_URL}/admin`,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 重试配置
const MAX_RETRIES = 2
const RETRY_DELAY = 1000 // 毫秒

// 请求拦截器：添加 Token + 处理 FormData（multipart）请求
api.interceptors.request.use(config => {
  // FormData / File 上传：删除默认 Content-Type，让浏览器自动加 multipart/form-data; boundary=...
  // 否则 axios 的 application/json 默认头会覆盖，导致后端 multipart 解析失败
  if (config.data instanceof FormData) {
    if (config.headers) {
      delete config.headers['Content-Type']
      delete config.headers['content-type']
    }
  }
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 修复：PUT / DELETE 请求无 body 时，axios 默认不发送 Content-Type 头，
  // Spring MVC 会返回 415 Unsupported Media Type。
  // 显式设置 application/json + 空 body {}，保证后端能正确解析。
  const method = String(config.method || '').toUpperCase()
  if ((method === 'PUT' || method === 'POST') && !config.data && !(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json'
    config.data = {}
  }
  return config
}, error => {
  return Promise.reject(error)
})

// 响应拦截器：统一解包 + 错误处理 + 自动重试
api.interceptors.response.use(
  // 成功响应：自动解包 { code, data, message } 格式
  response => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    // 后端返回了业务错误码（如 code=500），reject 让调用方处理
    // 携带 code 字段，便于登录页等场景区分"账号被锁(code=423)" / "密码错(code=401)"
    if (body && body.code !== undefined && body.code !== 0) {
      const bizErr = new Error(body.message || '操作失败')
      bizErr.code = body.code
      bizErr.business = true
      return Promise.reject(bizErr)
    }
    // 非标准格式原样返回
    return body
  },

  // 失败响应：自动重试 + 错误提示
  async error => {
    const config = error.config

    // 401 未登录：清理 Token 并跳转登录页（避免重复跳转和页面刷新的 ERR_ABORTED）
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('admin_token')
      const currentPath = router.currentRoute.value.path
      if (!isRedirectingToLogin && !currentPath.startsWith('/login')) {
        isRedirectingToLogin = true
        router.push('/login').finally(() => {
          isRedirectingToLogin = false
        })
      }
      return Promise.reject(error)
    }

    // 网络异常（非业务响应，如 ECONNRESET / ETIMEDOUT / ENOTFOUND）才重试
    // 业务 HTTP 错误（4xx/5xx）由服务端兜底，重试只会重复触发同样的错误，且
    // POST/PUT/PATCH/DELETE 等非幂等接口重试会造成重复扣款/重复发货等严重事故
    const isNetworkError = !error.response
    if (!isNetworkError) {
      // 业务错误：直接走错误提示路径，不重试
      return showErrorAndReject(error)
    }

    // 网络错误：自动重试（最多 MAX_RETRIES 次），且仅重试幂等方法
    // GET/HEAD 天然幂等；POST 等非幂等方法在网络层抖动时由服务端幂等性兜底（参见后端 @OperationLog + 业务幂等键）
    const method = (config?.method || 'get').toLowerCase()
    const isIdempotent = method === 'get' || method === 'head'
    if (!isIdempotent) {
      // 非幂等方法的网络错误：避免重复提交，直接提示用户重试
      return showErrorAndReject(error)
    }
    if (config && !config._retryCount) {
      config._retryCount = 0
    }
    if (config && config._retryCount < MAX_RETRIES) {
      config._retryCount++
      // 指数退避：1s, 2s, 4s...避免对已恢复的下游雪崩式重试
      await new Promise(resolve => setTimeout(resolve, RETRY_DELAY * config._retryCount))
      return api(config)
    }

    // 重试耗尽后显示错误提示
    return showErrorAndReject(error)
  }
)

/**
 * 统一错误提示 + reject：业务错误与非幂等方法网络错误走这条路径
 * 避免重复书写 ElMessage + statusMessages 表
 */
function showErrorAndReject(error) {
  const status = error.response?.status
  const statusMessages = {
    400: '请求参数有误',
    403: '没有访问权限',
    404: '请求的资源不存在',
    408: '请求超时',
    409: '操作冲突，请刷新后重试',
    429: '请求过于频繁，请稍后再试',
    500: '服务器内部错误',
    502: '网关错误',
    503: '服务暂时不可用'
  }

  // 优先使用后端返回的业务 message（如 "审核记录已处理"），
  // 否则按 HTTP 状态码映射，最后兜底 axios 自身错误信息
  const backendMsg = error.response?.data?.message
  const message = (backendMsg && String(backendMsg).trim())
    || statusMessages[status]
    || error.message
    || '网络异常，请检查网络连接'
  ElMessage.error(message)

  // 若后端返回了具体错误信息，记录到控制台便于排查
  if (backendMsg) {
    console.warn('API Error Detail:', backendMsg)
  }

  return Promise.reject(error)
}

export default api
