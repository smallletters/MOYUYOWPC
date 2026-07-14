/**
 * MOYUYO ATELIER 配置文件
 * 通过 Vite 环境变量注入，避免硬编码敏感信息
 * 所有以 VITE_ 开头的变量会被打包到客户端
 * 敏感凭证（如 Consumer Secret）生产环境必须经由 API 代理层保护
 */

// 通用 H5 端从 window.__MOYUYO_CONFIG__ 注入（index.html 模板）
// 移动端从 process.env 注入（Vite 编译时）
function getEnv(key, defaultValue = '') {
  if (typeof window !== 'undefined' && window.__MOYUYO_CONFIG__?.[key]) {
    return window.__MOYUYO_CONFIG__[key]
  }
  if (typeof process !== 'undefined' && process.env?.[key]) {
    return process.env[key]
  }
  return defaultValue
}

export const config = {
  // WooCommerce REST API 基础路径（含 /wp-json 前缀）
  apiBase: getEnv('VITE_API_BASE', 'https://your-wp-site.com/wp-json'),
  // WordPress 站点根地址（用于支付 WebView 跳转）
  wpBase: getEnv('VITE_WP_BASE', 'https://your-wp-site.com'),
  // WooCommerce REST API 凭证
  consumerKey: getEnv('VITE_CONSUMER_KEY', ''),
  consumerSecret: getEnv('VITE_CONSUMER_SECRET', ''),
  // WP OAuth Server 配置（MiniOrange）
  // 在 WP 后台 → WP OAuth Server → Client 中创建 App 获取
  oauthClientId: getEnv('VITE_OAUTH_CLIENT_ID', ''),
  oauthClientSecret: getEnv('VITE_OAUTH_CLIENT_SECRET', ''),
  // OAuth 端点（WP OAuth Server 插件）
  oauthTokenEndpoint: '/api/v1/token',        // POST: grant_type=password 登录
  oauthUserInfoEndpoint: '/api/v1/userinfo',  // GET: 获取当前用户信息
  oauthRefreshEndpoint: '/api/v1/token',      // POST: grant_type=refresh_token
  // 货币
  currency: getEnv('VITE_CURRENCY', 'USD'),
  // 主题
  themeMode: getEnv('VITE_THEME_MODE', 'system')
}

// API 版本号
export const WC_API_VERSION = 'wc/v3'
export const WP_API_VERSION = 'wp/v2'

// 请求超时
export const REQUEST_TIMEOUT = 15000

// 业务状态码
export const RESPONSE_CODE = {
  SUCCESS: 0,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  SERVER_ERROR: 500
}
