import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api/admin',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 重试配置
const MAX_RETRIES = 2
const RETRY_DELAY = 1000 // 毫秒

// 请求拦截器：添加 Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => {
  return Promise.reject(error)
})

// 响应拦截器：统一解包 + 错误处理 + 自动重试
api.interceptors.response.use(
  // 成功响应：自动解包 { code: 200, data: ... } 格式
  response => {
    const body = response.data
    // 后端统一返回 { code, data, message } 格式，解出 data
    if (body && body.code === 0) {
      return body.data
    }
    // 非标准格式原样返回
    return body
  },

  // 失败响应：自动重试 + 错误提示
  async error => {
    const config = error.config

    // 401 未登录：跳转登录页
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('admin_token')
      window.location.href = '/admin/login'
      return Promise.reject(error)
    }

    // 非 401 错误：自动重试（最多 MAX_RETRIES 次）
    if (config && !config._retryCount) {
      config._retryCount = 0
    }
    if (config && config._retryCount < MAX_RETRIES) {
      config._retryCount++
      // 延迟后重试
      await new Promise(resolve => setTimeout(resolve, RETRY_DELAY * config._retryCount))
      return api(config)
    }

    // 重试耗尽后显示错误提示
    const status = error.response?.status
    const statusMessages = {
      400: '请求参数有误',
      403: '没有访问权限',
      404: '请求的资源不存在',
      408: '请求超时',
      429: '请求过于频繁，请稍后再试',
      500: '服务器内部错误',
      502: '网关错误',
      503: '服务暂时不可用'
    }

    const message = statusMessages[status] || error.message || '网络异常，请检查网络连接'
    ElMessage.error(message)

    // 若后端返回了具体错误信息，优先使用
    if (error.response?.data?.message) {
      console.warn('API Error Detail:', error.response.data.message)
    }

    return Promise.reject(error)
  }
)

export default api
