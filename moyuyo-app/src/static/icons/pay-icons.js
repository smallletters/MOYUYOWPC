// 支付方式 logo 的 SVG 内联字符串
// 原因:APP 真机 (iOS WKWebView / Android WebView) 对 <uni-image> 加载 /static/icons/*.svg
//       路径下的 SVG 偶发渲染失败 (节点存在但内容为空,容器白盒)。
//       解决:把这些 SVG 内联成 base64 data URI 再赋给 <image :src>,
//       完全避开静态资源路径解析,真机/H5 都能稳定显示。
//
// 使用方式 (Vue 组件内):
//   import { svgToDataUri, PAY_ICONS_SVG } from '@/static/icons/pay-icons'
//   data() { return { icons: { googlepay: '', applepay: '', paypal: '', card: '' } } }
//   onLoad() {
//     Object.keys(this.icons).forEach((k) => {
//       this.icons[k] = svgToDataUri(PAY_ICONS_SVG[k])
//     })
//   }
// 然后在模板里用 :src="icons[p.idKey]"

// Google Pay (G 字标,灰色)
export const GOOGLEPAY_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="48" height="24"><path fill="#5F6368" d="M12.114 10.965v3.84h5.252c-.216 1.176-1.668 3.444-5.252 3.444-3.156 0-5.736-2.616-5.736-5.844s2.58-5.844 5.736-5.844c1.8 0 3.012.768 3.696 1.428l2.52-2.424C16.704 4.296 14.598 3.36 12.114 3.36 7.026 3.36 2.946 7.44 2.946 12.528s4.08 9.168 9.168 9.168c5.292 0 8.796-3.72 8.796-8.952 0-.6-.072-1.056-.156-1.488l-8.64-.288Z"/></svg>'

// Apple Pay (苹果 logo)
export const APPLEPAY_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="48" height="24"><path fill="#000000" d="M17.72 12.78c-.04-2.7 2.21-4 2.31-4.06-1.26-1.84-3.22-2.09-3.92-2.12-1.67-.17-3.25.98-4.1.98-.85 0-2.16-.96-3.55-.93-1.82.03-3.5 1.06-4.44 2.69-1.9 3.29-.49 8.16 1.36 10.83.9 1.31 1.97 2.78 3.37 2.73 1.36-.06 1.87-.87 3.51-.87 1.64 0 2.1.87 3.53.84 1.46-.02 2.38-1.33 3.27-2.65 1.03-1.52 1.45-3 1.47-3.07-.03-.01-2.82-1.08-2.86-4.29Zm-2.71-7.86c.74-.9 1.24-2.14 1.11-3.39-1.07.04-2.36.71-3.13 1.6-.69.79-1.3 2.06-1.13 3.28 1.19.09 2.42-.6 3.15-1.49Z"/></svg>'

// PayPal (双 P 蓝标)
export const PAYPAL_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="48" height="24"><path fill="#003087" d="M7.076 21.337H2.47a.641.641 0 0 1-.633-.74L4.944.902C5.005.402 5.464 0 5.964 0h7.886c2.36 0 4.166.61 5.36 1.81 1.114 1.12 1.628 2.66 1.628 4.45 0 .27-.014.546-.04.82-.32 2.61-1.69 4.49-3.85 5.51-1.4.66-3.13.99-5.06.99h-1.36a1.06 1.06 0 0 0-1.05.91l-.86 5.47-.014.094-.51 3.18a.641.641 0 0 1-.633.55Z"/><path fill="#009cde" d="M21.482 6.27c-.022.143-.046.285-.075.426-.78 4.04-3.45 5.43-6.86 5.43h-1.73a.853.853 0 0 0-.84.726l-.92 5.82-.26 1.65a.448.448 0 0 0 .443.516h3.08a.745.745 0 0 0 .736-.633l.018-.097.58-3.66.038-.205a.745.745 0 0 1 .736-.633h.464c3.01 0 5.37-1.22 6.06-4.76.29-1.48.14-2.72-.62-3.59a3 3 0 0 0-.85-.99Z"/></svg>'

// Card (通用信用卡:深蓝底+黄条+白色芯片)
export const CARD_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="48" height="24"><rect x="2" y="6" width="20" height="14" rx="2" fill="#1A1F71"/><rect x="2" y="9" width="20" height="3" fill="#F0C14B"/><rect x="4" y="14.5" width="4" height="1.6" rx="0.4" fill="#FFFFFF" opacity="0.85"/></svg>'

export const PAY_ICONS_SVG = {
  googlepay: GOOGLEPAY_SVG,
  applepay: APPLEPAY_SVG,
  paypal: PAYPAL_SVG,
  card: CARD_SVG,
}

/**
 * 把单条 SVG 字符串编码成 data URI;只在调用时执行,避免模块顶层触发
 * 优先使用 uni 全局 (APP/H5 都有),浏览器端降级到 btoa + TextEncoder
 */
export function svgToDataUri(svg) {
  let b64
  if (typeof uni !== 'undefined' && typeof uni.arrayBufferToBase64 === 'function') {
    const ab =
      typeof uni.stringToArrayBuffer === 'function'
        ? uni.stringToArrayBuffer(svg)
        : new TextEncoder().encode(svg)
    b64 = uni.arrayBufferToBase64(ab)
  } else if (typeof btoa === 'function' && typeof TextEncoder !== 'undefined') {
    // 把 UTF-8 字节走 TextEncoder 再按字节喂给 btoa,避免 unescape 已废弃
    const bytes = new TextEncoder().encode(svg)
    let bin = ''
    bytes.forEach((b) => (bin += String.fromCharCode(b)))
    b64 = btoa(bin)
  } else {
    b64 = Buffer.from(svg, 'utf-8').toString('base64')
  }
  return 'data:image/svg+xml;base64,' + b64
}

/**
 * 一次性把全部支付方式 logo 转成 data URI 映射
 */
export function buildPayIconDataUris() {
  const out = {}
  Object.keys(PAY_ICONS_SVG).forEach((k) => {
    out[k] = svgToDataUri(PAY_ICONS_SVG[k])
  })
  return out
}
