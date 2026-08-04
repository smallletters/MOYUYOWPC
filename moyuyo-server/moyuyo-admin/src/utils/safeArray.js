/**
 * 安全地将API响应转换为数组
 * 解决后端返回 { code: 0, data: {} } 时 res.records || res || [] 返回对象而非数组的问题
 * 
 * @param {*} res - API响应数据（已由拦截器解包）
 * @param {string} [key] - 可选的嵌套数组key，如 'records', 'list', 'data', 'items'
 * @returns {Array} 安全的数据数组
 */
export function toArray(res, key) {
  if (Array.isArray(res)) return res
  if (!res || typeof res !== 'object') return []
  // 按优先级检查常见嵌套字段
  const keys = key ? [key] : ['records', 'list', 'data', 'items']
  for (const k of keys) {
    if (Array.isArray(res[k])) return res[k]
  }
  return []
}
