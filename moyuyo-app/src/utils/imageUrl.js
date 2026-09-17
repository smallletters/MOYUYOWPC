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
 * - 以 / 开头且有 base：拼上 base
 * - 其余（裸文件名 / data URI 等）：原样返回
 *
 * @param {string} url 后端返回的图片 URL
 * @returns {string} 浏览器/原生 <image> 可直接加载的 URL
 */
export function toAbsoluteImageUrl(url) {
  if (!url) return url
  if (/^https?:\/\//i.test(url)) return url
  // APP 端运行时 process.env.VITE_ADMIN_API_BASE 由 vite define 在编译期字面量替换；
  // H5 端由 vite proxy 处理（base 留空），同样能走通。
  const base =
    (typeof process !== 'undefined' && process.env && process.env.VITE_ADMIN_API_BASE) || ''
  if (typeof url === 'string' && url.startsWith('/') && base) {
    return `${base}${url}`
  }
  return url
}
