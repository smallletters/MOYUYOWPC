/**
 * 把后端返回的图片/头像 URL 转成 <image> 能加载的绝对地址。
 *
 * 后端 controller 通常把上传资源存为相对路径，例如 "/uploads/2026/09/abc.jpg"。
 * - H5 端：直接以同源或 vite proxy 拉取能成功，相对路径也能用。
 * - APP 端：<image> 不会走 vite proxy，相对路径会被原生解析成当前 app 域下的资源，
 *   而真实文件其实在后端 host 上，必须把 base（VITE_ADMIN_API_BASE）拼上。
 *
 * 行为：
 * - 空值：原样返回（让调用方继续用 defaultAvatar 兜底）
 * - 已经是 http(s):// 的：原样返回（兼容 OAuth / 三方头像）
 * - 以 /static/ 开头：原样返回（uni-app 内置静态资源，随包打进 APP，绝不能拼后端 base）
 * - 其余以 / 开头且有 base：拼上 base
 * - 其余（裸文件名 / data URI 等）：原样返回
 *
 * @param {string} url 后端返回的图片 URL
 * @returns {string} 浏览器/原生 <image> 可直接加载的 URL
 */
export function toAbsoluteImageUrl(url) {
  if (!url) return url
  if (/^https?:\/\//i.test(url)) return url
  // 与 request.js 的既有约定保持一致：vite.config.js 的 define 会在编译期把
  // process.env.VITE_ADMIN_API_BASE 静态替换成字符串字面量（APP 端同样生效）。
  // 绝对不能用 typeof process !== 'undefined' 做运行时守卫：uni-app APP 端
  // typeof process === 'undefined'，守卫会让整个 && 链短路、base 恒为空，
  // 相对路径 /uploads/... 就拼不成绝对 URL，原生 <image> 加载失败显示空白。
  // 也不能用 import.meta.env（vite-plugin-uni APP 端 polyfill 会用到 new URL/document 导致白屏）。
  const base = process.env.VITE_ADMIN_API_BASE || ''
  if (typeof url === 'string' && url.startsWith('/') && base) {
    // /static/** 是 uni-app 内置静态资源（随包打进 APP，H5 由 Vite dev server 提供），
    // 属于「本地资源」而非后端资源，拼上后端 base 会 404 —— 必须先排除。
    if (url.startsWith('/static/')) return url
    // /uploads/** 等后端相对路径：补成 http(s)://host/...
    return `${base}${url}`
  }
  return url
}
