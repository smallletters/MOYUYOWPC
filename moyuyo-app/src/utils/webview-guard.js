import { config } from './config'

/**
 * WebView URL 安全守卫。
 *
 * 背景:uni-app 的 <web-view> 加载任何 https URL 都会被渲染,而原生 webview
 * 内运行的页面可访问 uni JSAPI(支付/分享/跳转)进而被利用为钓鱼/钓鱼中转。
 * 必须限制仅允许在白名单内的 host 加载。
 *
 * 用法:
 *   if (!isUrlAllowed(url)) { uni.showToast({...}); return }
 *   uni.navigateTo({ url: `/pages/webview/document?url=${encodeURIComponent(url)}` })
 */
export function isUrlAllowed(url) {
  if (!url || typeof url !== 'string') return false
  // 只允许 http(s),禁止 file/javascript/data 等危险协议
  if (!/^https?:\/\//i.test(url)) return false
  let host
  try {
    host = new URL(url).host.toLowerCase()
  } catch (e) {
    return false
  }
  const allowed = (config.webviewAllowedHosts || []).map((h) => h.toLowerCase())
  // 精确匹配 或 子域名匹配(如 *.moyuyo.com)
  return allowed.some((allowedHost) => {
    const norm = allowedHost.replace(/^www\./, '')
    return host === norm || host === `www.${norm}` || host.endsWith(`.${norm}`)
  })
}

/**
 * 校验并返回;不通过则 toast 错误。
 * 适合在 navigateTo 前一行调用:
 *   guardUrlOrToast(url) || uni.navigateTo({ url: ... })
 */
export function guardUrlOrToast(url) {
  if (isUrlAllowed(url)) return true
  uni.showToast({ title: '外链不在白名单,已拦截', icon: 'none', duration: 2500 })
  return false
}

export default { isUrlAllowed, guardUrlOrToast }
