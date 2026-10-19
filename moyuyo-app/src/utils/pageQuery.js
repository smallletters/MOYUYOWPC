/**
 * 读取当前页面的 URL query（跨端统一入口）。
 *
 * 背景（已核对 @dcloudio/uni-h5 与 @dcloudio/uni-app-plus 运行时源码）：
 * - H5（uni-h5 setupPage）：同时把 query 写到
 *     `pageVm.options`        和 `pageVm.$page.options`
 * - APP（uni-app-plus initPageVm）：只把 internal page 挂到
 *     `pageVm.$page`（内含 options 字段）
 *   **不会** 给 `pageVm.options` 赋值。
 *
 * 因此 `getCurrentPages().slice(-1)[0].options` 在 H5 上能拿到 `?from=checkout`，
 * 在 APP 真机上恒为 undefined —— 这是「结算页改地址后 APP 端点地址没反应」的根因。
 *
 * 本函数按可靠度从高到低依次尝试多条路径，任意一端都能取到 query。
 *
 * @returns {Record<string, string>} URL query；取不到时返回空对象（永远非 null）
 */
function toSafeQuery(value) {
  return value && typeof value === 'object' ? value : {}
}

/**
 * 获取当前页面上一次 navigate 带过来的参数。
 * @returns {Record<string, string>} query 对象
 */
export function getPageQuery() {
  try {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const cur = pages[pages.length - 1]
    if (!cur) return {}
    // 路径 1：H5 / 部分小程序把 options 直接挂在页面实例上
    if (cur.options) return toSafeQuery(cur.options)
    // 路径 2：APP 端唯一可用路径（$page 由 initPageVm 挂载，内含 options）
    const page = cur.$page || (cur.$vm && cur.$vm.$page)
    if (page) {
      if (page.options) return toSafeQuery(page.options)
      // 路径 3：个别版本把查询串放在 $page.query 上
      if (page.query) return toSafeQuery(page.query)
    }
    return {}
  } catch (e) {
    // getCurrentPages 在极少数异常上下文会抛错，这里吞掉并按无参数处理
    console.warn('[pageQuery] read page query failed', e)
    return {}
  }
}
