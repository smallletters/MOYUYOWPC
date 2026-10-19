/**
 * MOYUYO ATELIER 配置文件
 * 通过 Vite 环境变量注入，避免硬编码敏感信息
 * 所有以 VITE_ 开头的变量会被打包到客户端
 * 敏感凭证（如 Consumer Secret）生产环境必须经由 API 代理层保护
 */

// 通用 H5 端从 window.__MOYUYO_CONFIG__ 注入（index.html 模板）
// 移动端从 process.env 注入（Vite 编译时）
//
// ⚠️ 重要（与 imageUrl.js / request.js 同一条铁律）：
// vite.config.js 的 define 只能在编译期把「静态字面量」process.env.VITE_X 替换成字符串。
// 它无法替换动态下标访问 process.env[key]，也无法穿透 typeof process !== 'undefined' 守卫
// （守卫是运行时求值，APP 端 typeof process === 'undefined' 会整段短路）。
// 所以这里必须先用显式属性访问把需要的变量收集成字面量对象，getEnv 再从这个对象取值。
// 新增变量时请同步加到本对象，并确保 .env / .env.<mode> 中有对应定义（否则打包后该处
// 仍是 process.env.VITE_X，APP 端会因 process 未定义而报错）。
const ENV = {
  VITE_API_BASE: process.env.VITE_API_BASE,
  VITE_WP_BASE: process.env.VITE_WP_BASE,
  VITE_PAY_RETURN_BASE: process.env.VITE_PAY_RETURN_BASE,
  VITE_CONSUMER_KEY: process.env.VITE_CONSUMER_KEY,
  VITE_CONSUMER_SECRET: process.env.VITE_CONSUMER_SECRET,
  VITE_OAUTH_CLIENT_ID: process.env.VITE_OAUTH_CLIENT_ID,
  VITE_OAUTH_CLIENT_SECRET: process.env.VITE_OAUTH_CLIENT_SECRET,
  VITE_CURRENCY: process.env.VITE_CURRENCY,
  VITE_THEME_MODE: process.env.VITE_THEME_MODE,
  VITE_TERMS_URL: process.env.VITE_TERMS_URL,
  VITE_PRIVACY_URL: process.env.VITE_PRIVACY_URL,
  VITE_QUALIFICATION_URL: process.env.VITE_QUALIFICATION_URL,
  VITE_LICENSE_URL: process.env.VITE_LICENSE_URL,
  VITE_CONTACT_WEBSITE: process.env.VITE_CONTACT_WEBSITE,
  VITE_CONTACT_EMAIL: process.env.VITE_CONTACT_EMAIL,
  VITE_CONTACT_PHONE: process.env.VITE_CONTACT_PHONE,
  VITE_SOCIAL_GOOGLE_ENABLED: process.env.VITE_SOCIAL_GOOGLE_ENABLED,
  VITE_SOCIAL_APPLE_ENABLED: process.env.VITE_SOCIAL_APPLE_ENABLED,
  VITE_SOCIAL_GOOGLE_URL: process.env.VITE_SOCIAL_GOOGLE_URL,
  VITE_WEBVIEW_ALLOWED_HOSTS: process.env.VITE_WEBVIEW_ALLOWED_HOSTS,
}

function getEnv(key, defaultValue = '') {
  // H5 端优先用 index.html 注入的 window.__MOYUYO_CONFIG__
  if (typeof window !== 'undefined' && window.__MOYUYO_CONFIG__?.[key]) {
    return window.__MOYUYO_CONFIG__[key]
  }
  const v = ENV[key]
  if (v) return v
  return defaultValue
}

export const config = {
  // WooCommerce REST API 基础路径（含 /wp-json 前缀）
  apiBase: getEnv('VITE_API_BASE', 'https://your-wp-site.com/wp-json'),
  // WordPress 站点根地址（用于支付 WebView 跳转）
  wpBase: getEnv('VITE_WP_BASE', 'https://your-wp-site.com'),
  // MOYUYO 后端 API 域名（H1 修复：用于支付成功/取消回跳的绝对 URL，避免与 WP 域名混淆）
  // dev 默认留空，使用 Vite proxy 同源；prod 必须填 https://api.moyuyo.com
  payReturnBase: getEnv('VITE_PAY_RETURN_BASE', ''),
  // WooCommerce REST API 凭证
  consumerKey: getEnv('VITE_CONSUMER_KEY', ''),
  consumerSecret: getEnv('VITE_CONSUMER_SECRET', ''),
  // WP OAuth Server 配置（MiniOrange）
  // 在 WP 后台 → WP OAuth Server → Client 中创建 App 获取
  oauthClientId: getEnv('VITE_OAUTH_CLIENT_ID', ''),
  oauthClientSecret: getEnv('VITE_OAUTH_CLIENT_SECRET', ''),
  // OAuth 端点（WP OAuth Server 插件）
  oauthTokenEndpoint: '/api/v1/token', // POST: grant_type=password 登录
  oauthUserInfoEndpoint: '/api/v1/userinfo', // GET: 获取当前用户信息
  oauthRefreshEndpoint: '/api/v1/token', // POST: grant_type=refresh_token
  // 货币
  currency: getEnv('VITE_CURRENCY', 'USD'),
  // 主题
  themeMode: getEnv('VITE_THEME_MODE', 'system'),
  // 法定协议链接(未配置时 about 页跳转到内置兜底页)
  termsUrl: getEnv('VITE_TERMS_URL', ''),
  privacyUrl: getEnv('VITE_PRIVACY_URL', ''),
  qualificationUrl: getEnv('VITE_QUALIFICATION_URL', ''),
  licenseUrl: getEnv('VITE_LICENSE_URL', ''),
  // 客服联系方式(about 页)
  // 上线前必须改为真实号码/邮箱/官网
  contactWebsite: getEnv('VITE_CONTACT_WEBSITE', 'www.moyuyoshop.com'),
  contactEmail: getEnv('VITE_CONTACT_EMAIL', 'moyuyo@moyuyoshop.com'),
  contactPhone: getEnv('VITE_CONTACT_PHONE', '400-888-MOYU'),
  // 社会化登录开关：登录页 Google / Apple 按钮是否展示
  // - googleEnabled: 设为 'true' 后显示 Google 按钮(VITE_SOCIAL_GOOGLE_URL 必填)
  // - appleEnabled:  设为 'true' 后显示 Apple 按钮
  // 未启用时按钮隐藏,避免上线后用户看到 "coming soon" 占位
  socialGoogleEnabled: getEnv('VITE_SOCIAL_GOOGLE_ENABLED', 'false') === 'true',
  socialAppleEnabled: getEnv('VITE_SOCIAL_APPLE_ENABLED', 'false') === 'true',
  socialGoogleUrl: getEnv('VITE_SOCIAL_GOOGLE_URL', ''),
  // WebView 安全白名单：仅允许在白名单中的 host 打开外部 web-view
  // 多 host 用英文逗号分隔,如 "moyuyo.com,www.moyuyo.com,your-wp-site.com"
  // 默认包含自身官网域名(根据上面 contactWebsite 自动派生)
  // 注意:此处不使用 IIFE 直接计算默认值(避免 Vite 在某些情况下把 IIFE 标记为副作用并错误求值)
  webviewAllowedHosts: (() => {
    const explicit = getEnv('VITE_WEBVIEW_ALLOWED_HOSTS', '')
    if (explicit)
      return explicit
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
    // 自动派生
    const w = getEnv('VITE_CONTACT_WEBSITE', 'www.moyuyo.com')
    const stripped = w.replace(/^https?:\/\//, '').replace(/^www\./, '')
    const withWww = w.startsWith('www.') ? w : `www.${w}`
    return Array.from(new Set([stripped, withWww, 'your-wp-site.com']))
  })(),
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
  SERVER_ERROR: 500,
}
