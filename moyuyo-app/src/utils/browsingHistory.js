/**
 * 浏览记录 - 纯本地存储工具
 *
 * 设计目的：
 * 用户要求浏览记录不再存储到后端，仅在 APP 本地保留，方便快速访问最近浏览过的商品。
 *
 * 数据结构：
 * - 存储 key：`moyuyo_browsing_history`（H5/APP 通用 uni.storage）
 * - 单条记录字段：{ id, name, image, price, viewTime, viewTimestamp, group, dateLabel }
 * - 分组（group）：今天 / 昨天 / 本周内 / 本周前，用于页面分组渲染
 *
 * 容量策略：
 * - 最多保留 500 条
 * - 超过 90 天的记录自动清理
 * - 同一商品重复浏览仅更新时间戳并置顶
 */

// 本地存储键名（H5/APP 通用）
const STORAGE_KEY = 'moyuyo_browsing_history'
// 最大保留条数
const MAX_RECORDS = 500
// 保留天数（与页面 footer 提示文案 "记录保留 90 天，最多 500 条" 保持一致）
const RETENTION_DAYS = 90

/**
 * 读取全部浏览记录
 * @returns {Array} 记录列表（按浏览时间倒序：最新在前）
 */
export function getAll() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) return []
    const list = Array.isArray(raw) ? raw : []
    // 懒清理：读取时顺手把过期记录剔除，避免页面渲染时还要二次过滤
    return pruneExpired(list)
  } catch (e) {
    // 读取失败兜底为空列表，避免页面崩溃
    console.warn('[browsingHistory] getAll failed:', e)
    return []
  }
}

/**
 * 记录一次浏览。同一商品（按 id 去重）仅更新时间戳并置顶。
 * @param {{id:string|number,name:string,image:string,price:number|string}} product
 */
export function recordView(product) {
  if (!product || product.id == null) return
  try {
    const list = getAll()
    const id = String(product.id)
    // 过滤掉同 id 的旧记录，确保最新一次置顶
    const filtered = list.filter((it) => String(it.id) !== id)
    const now = Date.now()
    const record = {
      id,
      name: product.name || '',
      image: product.image || '',
      price: product.price ?? '',
      viewTimestamp: now,
    }
    filtered.unshift(record)
    // 写入前再做一次容量 + 过期控制
    const pruned = enforceLimits(filtered)
    uni.setStorageSync(STORAGE_KEY, pruned)
  } catch (e) {
    console.warn('[browsingHistory] recordView failed:', e)
  }
}

/**
 * 批量删除指定 id 的记录
 * @param {Array<string|number>} ids
 */
export function deleteByIds(ids) {
  if (!Array.isArray(ids) || ids.length === 0) return getAll()
  try {
    const idSet = new Set(ids.map((v) => String(v)))
    const list = getAll().filter((it) => !idSet.has(String(it.id)))
    uni.setStorageSync(STORAGE_KEY, list)
    return list
  } catch (e) {
    console.warn('[browsingHistory] deleteByIds failed:', e)
    return getAll()
  }
}

/**
 * 清空全部记录
 */
export function clearAll() {
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch (e) {
    console.warn('[browsingHistory] clearAll failed:', e)
  }
}

/**
 * 兜底：当 list 为空或数据陈旧时写入空数组（页面读取前的初始化）
 */
export function initIfEmpty() {
  const cur = getAll()
  if (!Array.isArray(cur) || cur.length === 0) {
    try {
      uni.setStorageSync(STORAGE_KEY, [])
    } catch (e) {
      // ignore
    }
  }
}

// ---------- 内部辅助 ----------

/**
 * 剔除过期记录（超过 RETENTION_DAYS 天）
 */
function pruneExpired(list) {
  const cutoff = Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000
  return list.filter((it) => Number(it.viewTimestamp) >= cutoff)
}

/**
 * 强制容量 + 过期限制：保留前 MAX_RECORDS 条并剔除过期项
 */
function enforceLimits(list) {
  const pruned = pruneExpired(list)
  return pruned.slice(0, MAX_RECORDS)
}

/**
 * 工具：把时间戳格式化为页面展示用的 "HH:mm"
 */
export function formatTime(ts) {
  const d = new Date(Number(ts) || Date.now())
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 工具：基于时间戳生成分组键与日期标签
 * 返回 { group: 'today'|'yesterday'|'thisWeek'|'earlier', dateLabel: '今天'|'昨天'|'x 天前'|'yyyy-MM-dd' }
 */
export function buildGroup(ts) {
  const date = new Date(Number(ts) || Date.now())
  const now = new Date()
  // 今天 0 点
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const yesterdayStart = todayStart - 24 * 60 * 60 * 1000
  // 本周一 0 点（周一作为一周开始）
  const day = now.getDay() === 0 ? 7 : now.getDay()
  const weekStart = todayStart - (day - 1) * 24 * 60 * 60 * 1000
  const t = date.getTime()

  if (t >= todayStart) return { group: 'today', dateLabel: '今天' }
  if (t >= yesterdayStart) return { group: 'yesterday', dateLabel: '昨天' }
  if (t >= weekStart) return { group: 'thisWeek', dateLabel: '本周' }
  // 更早：按 x 天前显示，简化展示
  const days = Math.floor((todayStart - t) / (24 * 60 * 60 * 1000))
  if (days < RETENTION_DAYS) return { group: 'earlier', dateLabel: `${days} 天前` }
  const pad = (n) => String(n).padStart(2, '0')
  return {
    group: 'earlier',
    dateLabel: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
  }
}

export default {
  getAll,
  recordView,
  deleteByIds,
  clearAll,
  initIfEmpty,
  formatTime,
  buildGroup,
}
