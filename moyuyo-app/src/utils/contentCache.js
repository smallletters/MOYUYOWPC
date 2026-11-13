/**
 * 内容缓存（商品列表 / 社区信息流等）
 *
 * 设计目标：
 * 1. 先渲染本地缓存，再请求接口刷新（stale-while-revalidate），弱网/离线首屏不空白
 * 2. 优先 IndexedDB（容量大）；不支持或打开失败时自动降级到 uni Storage
 * 3. 只缓存列表首页等小体量数据；key 由调用方按数据维度拼（tab/排序/筛选/用户）
 * 4. 过期策略：
 *    - 软过期 24h：仍返回数据（expired=true），保证离线可看，接口成功后覆盖
 *    - 硬保留 7 天：读取时超过即删除并返回 miss，避免旧数据无限堆积
 * 5. 淘汰策略：最多保留 200 条，写入后节流清理（5 分钟一次）；
 *    超期或超量均按 updatedAt 最旧优先淘汰；降级存储额外限制总容量
 *
 * IndexedDB 记录结构：
 *   key = 业务 key
 *   value = { key, data, updatedAt }
 * 降级存储 key：
 *   `moyuyo_content_cache:${key}`
 */

import { getStorage, setStorage, removeStorage } from '@/utils/storage'

const DB_NAME = 'moyuyo-content'
const DB_VERSION = 1
const STORE_NAME = 'entries'
const FALLBACK_PREFIX = 'moyuyo_content_cache'
// 软过期：24 小时后标记 expired，但仍返回数据，保证离线可看
export const CONTENT_CACHE_MAX_AGE = 24 * 60 * 60 * 1000
// 硬保留：超过 7 天的数据读取时直接删除，避免无限占用本地空间
const CONTENT_CACHE_RETENTION = 7 * 24 * 60 * 60 * 1000
// 最多保留的缓存条数，超出后按 updatedAt 最旧优先淘汰
const MAX_CACHE_ENTRIES = 200
// 清理节流间隔：两次全量清理至少间隔 5 分钟，避免每次写入都扫描
const PRUNE_INTERVAL = 5 * 60 * 1000
// 降级到 uni Storage 时单条记录的最大字符数（约 400KB，小程序单 key 上限 1MB）
const FALLBACK_MAX_CHARS = 400 * 1024
// 降级存储总量上限（字符数），超出后按最旧优先淘汰
const FALLBACK_MAX_TOTAL_CHARS = 2 * 1024 * 1024

// IndexedDB 是否已确认不可用（环境不支持或运行期打开失败）
let idbBroken = typeof indexedDB === 'undefined'
let dbPromise = null

/** 打开/复用数据库连接 */
function openDB() {
  if (dbPromise) return dbPromise
  if (typeof indexedDB === 'undefined') {
    idbBroken = true
    dbPromise = Promise.reject(new Error('IndexedDB not supported'))
    return dbPromise
  }
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        // keyPath 模式，主键直接来自 value.key
        db.createObjectStore(STORE_NAME, { keyPath: 'key' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error || new Error('openDB failed'))
  })
  // 连接失败后下次重新尝试，避免一直复用 rejected Promise
  dbPromise.catch(() => {
    dbPromise = null
  })
  return dbPromise
}

/** 通用读写事务包装 */
function withStore(mode, handler) {
  return openDB().then(
    (db) =>
      new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, mode)
        const store = transaction.objectStore(STORE_NAME)
        let result = null
        try {
          result = handler(store)
        } catch (e) {
          reject(e)
          return
        }
        transaction.oncomplete = () => resolve(result)
        transaction.onerror = () => reject(transaction.error)
        transaction.onabort = () => reject(transaction.error || new Error('transaction aborted'))
      }),
  )
}

/** 标记 IndexedDB 不可用，后续调用直接走 uni Storage 降级 */
function markIdbBroken(error) {
  if (!idbBroken) {
    console.warn('[contentCache] IndexedDB 不可用，降级为 uni Storage：', error && error.message)
  }
  idbBroken = true
}

/** 降级存储 key */
function fallbackKey(key) {
  return `${FALLBACK_PREFIX}:${key}`
}

/** 转成可结构化克隆的纯数据：Vue 响应式代理直接写 IndexedDB 会抛 DataCloneError */
function serialize(data) {
  try {
    return JSON.parse(JSON.stringify(data))
  } catch (e) {
    return data
  }
}

/** 降级读取 */
function readFallback(key) {
  const record = getStorage(fallbackKey(key), null)
  return record && typeof record === 'object' ? record : null
}

/** 降级写入：超过体积上限直接放弃，避免单 key 超限导致写入失败 */
function writeFallback(key, data) {
  let text = ''
  try {
    text = JSON.stringify(data)
  } catch (e) {
    return false
  }
  if (text.length > FALLBACK_MAX_CHARS) {
    console.warn('[contentCache] 缓存体积超出降级存储上限，跳过写入：', key)
    return false
  }
  const ok = setStorage(fallbackKey(key), {
    key,
    data,
    updatedAt: Date.now(),
    // 记录体积供淘汰策略计算总容量
    size: text.length,
  })
  if (ok) maybePruneCache()
  return ok
}

/**
 * 清理 IndexedDB：删除超过硬保留期的记录，再按 updatedAt 最旧优先删到条数上限
 */
async function pruneIdbCache() {
  if (idbBroken) return
  try {
    await withStore('readwrite', (store) => {
      const records = []
      const req = store.openCursor()
      req.onsuccess = () => {
        const cursor = req.result
        if (cursor) {
          records.push({
            key: cursor.key,
            updatedAt: Number(cursor.value && cursor.value.updatedAt) || 0,
          })
          cursor.continue()
          return
        }
        // 游标遍历完成后统一计算待删 key；此时事务仍活跃，可继续 enqueue 删除
        const cutoff = Date.now() - CONTENT_CACHE_RETENTION
        const toDelete = records.filter((item) => item.updatedAt < cutoff)
        const alive = records.filter((item) => item.updatedAt >= cutoff)
        alive.sort((a, b) => a.updatedAt - b.updatedAt)
        if (alive.length > MAX_CACHE_ENTRIES) {
          toDelete.push(...alive.splice(0, alive.length - MAX_CACHE_ENTRIES))
        }
        toDelete.forEach((item) => store.delete(item.key))
      }
      return true
    })
  } catch (e) {
    markIdbBroken(e)
  }
}

/**
 * 清理降级存储：硬保留期 + 条数上限 + 总容量预算，最旧优先淘汰
 */
function pruneFallbackCache() {
  try {
    const info = uni.getStorageInfoSync()
    const storagePrefix = `${FALLBACK_PREFIX}:`
    const items = ((info && info.keys) || [])
      .filter((storageKey) => storageKey.startsWith(storagePrefix))
      .map((storageKey) => {
        const record = getStorage(storageKey, null)
        return {
          key: storageKey,
          updatedAt: Number(record && record.updatedAt) || 0,
          size: Number(record && record.size) || 0,
        }
      })
    if (!items.length) return

    const cutoff = Date.now() - CONTENT_CACHE_RETENTION
    const toDelete = items.filter((item) => item.updatedAt < cutoff)
    const alive = items.filter((item) => item.updatedAt >= cutoff)
    // 按最旧优先淘汰到条数上限
    alive.sort((a, b) => a.updatedAt - b.updatedAt)
    while (alive.length > MAX_CACHE_ENTRIES) toDelete.push(alive.shift())
    // 再按总容量预算继续淘汰最旧记录
    let totalSize = alive.reduce((sum, item) => sum + item.size, 0)
    while (totalSize > FALLBACK_MAX_TOTAL_CHARS && alive.length) {
      const oldest = alive.shift()
      totalSize -= oldest.size
      toDelete.push(oldest)
    }
    toDelete.forEach((item) => removeStorage(item.key))
  } catch (e) {
    console.warn('[contentCache] 清理降级缓存失败', e)
  }
}

// 上次清理时间：写入后节流触发，避免每次写都全量扫描
let lastPruneAt = 0

/** 写入后节流清理缓存 */
function maybePruneCache() {
  const now = Date.now()
  if (now - lastPruneAt < PRUNE_INTERVAL) return
  lastPruneAt = now
  if (!idbBroken) pruneIdbCache()
  // 兼容曾降级写入的历史副本；无相关 key 时开销很小
  pruneFallbackCache()
}

/**
 * 读取缓存
 * @param {string} key 业务 key
 * @param {{maxAge?:number}} [options] maxAge 软过期时间（毫秒），默认 24 小时
 * @returns {Promise<{data:any, updatedAt:number, age:number, expired:boolean}|null>}
 *          无缓存/超硬保留期返回 null；软过期仍返回数据，仅 expired=true
 */
export async function readCache(key, options = {}) {
  if (!key) return null
  const maxAge = Number(options.maxAge) > 0 ? Number(options.maxAge) : CONTENT_CACHE_MAX_AGE

  let record = null
  if (!idbBroken) {
    try {
      const req = await withStore('readonly', (store) => store.get(key))
      record = req && req.result
    } catch (e) {
      markIdbBroken(e)
    }
  }
  // IDB 无记录时再查降级存储，兼容曾经降级写入的历史数据
  if (!record) record = readFallback(key)
  if (!record) return null
  // 结构损坏的记录直接清掉，避免永久占位且挡住降级副本
  if (record.data === undefined) {
    removeCache(key)
    return null
  }

  const updatedAt = Number(record.updatedAt) || 0
  const age = Date.now() - updatedAt
  // 超过硬保留期：直接删除并当作 miss，避免旧数据无限堆积
  if (age > CONTENT_CACHE_RETENTION) {
    removeCache(key)
    return null
  }
  return { data: record.data, updatedAt, age, expired: age > maxAge }
}

/**
 * 写入/覆盖缓存
 * @param {string} key 业务 key
 * @param {any} data 可 JSON 序列化的数据
 * @returns {Promise<boolean>}
 */
export async function writeCache(key, data) {
  if (!key) return false
  const payload = serialize(data)

  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        store.put({ key, data: payload, updatedAt: Date.now() })
        return true
      })
      // 同步清掉历史上的降级副本，避免 IDB 不可用时读到旧数据
      removeStorage(fallbackKey(key))
      // 写入后节流淘汰超期/超量缓存
      maybePruneCache()
      return true
    } catch (e) {
      markIdbBroken(e)
    }
  }
  return writeFallback(key, payload)
}

/**
 * 删除单条缓存（数据发生写操作后主动失效）
 * @param {string} key 业务 key
 * @returns {Promise<boolean>}
 */
export async function removeCache(key) {
  if (!key) return false
  let removed = false
  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        store.delete(key)
        return true
      })
      removed = true
    } catch (e) {
      markIdbBroken(e)
    }
  }
  // 降级副本一并删除，避免 IDB 不可用时旧缓存重新生效
  if (removeStorage(fallbackKey(key))) removed = true
  return removed
}

/**
 * 统计当前内容缓存的条数与占用（字符数近似字节数）
 * 供设置页「清除缓存」展示占用大小
 * @returns {Promise<{count:number, size:number}>}
 */
export async function getCacheStats() {
  let count = 0
  let size = 0

  if (!idbBroken) {
    try {
      await withStore('readonly', (store) => {
        const req = store.openCursor()
        req.onsuccess = () => {
          const cursor = req.result
          if (!cursor) return
          count += 1
          try {
            // 按序列化后的字符数估算占用，避免额外存储体积字段
            size += JSON.stringify(cursor.value && cursor.value.data).length
          } catch (e) {
            /* 单条序列化失败不影响整体统计 */
          }
          cursor.continue()
        }
        return true
      })
    } catch (e) {
      markIdbBroken(e)
    }
  }

  // 一并统计历史上降级写入 uni Storage 的副本（写入时已记录 size 字段）
  try {
    const keys = ((uni.getStorageInfoSync() || {}).keys || []).filter((key) =>
      key.startsWith(`${FALLBACK_PREFIX}:`),
    )
    keys.forEach((key) => {
      const record = getStorage(key, null)
      count += 1
      size += Number(record && record.size) || 0
    })
  } catch (e) {
    console.warn('[contentCache] 统计降级缓存失败', e)
  }

  return { count, size }
}

/**
 * 清空全部内容缓存（设置页「清除缓存」使用）
 * 同时清理 IndexedDB 与降级存储副本，不影响登录凭证与其他业务数据
 * @returns {Promise<boolean>} 是否执行过清理
 */
export async function clearAllCache() {
  let cleared = false

  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        store.clear()
        return true
      })
      cleared = true
    } catch (e) {
      markIdbBroken(e)
    }
  }

  try {
    const keys = ((uni.getStorageInfoSync() || {}).keys || []).filter((key) =>
      key.startsWith(`${FALLBACK_PREFIX}:`),
    )
    keys.forEach((key) => removeStorage(key))
    if (keys.length) cleared = true
  } catch (e) {
    console.warn('[contentCache] 清理降级缓存失败', e)
  }

  return cleared
}

/**
 * 按前缀批量删除缓存（如发帖后失效所有 Tab/账号的信息流缓存）
 * @param {string} prefix 业务 key 前缀，如 `community:feed:`
 * @returns {Promise<boolean>} 是否至少删除了一条
 */
export async function removeCacheByPrefix(prefix) {
  if (!prefix) return false
  let removed = false

  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        const req = store.openKeyCursor()
        req.onsuccess = () => {
          const cursor = req.result
          if (!cursor) return
          if (String(cursor.key).startsWith(prefix)) {
            store.delete(cursor.key)
            removed = true
          }
          cursor.continue()
        }
        return true
      })
    } catch (e) {
      markIdbBroken(e)
    }
  }

  // 同时清理降级存储（覆盖曾经降级写入的数据）
  try {
    const info = uni.getStorageInfoSync()
    const storagePrefix = `${FALLBACK_PREFIX}:${prefix}`
    ;((info && info.keys) || []).forEach((storageKey) => {
      if (storageKey.startsWith(storagePrefix)) {
        removeStorage(storageKey)
        removed = true
      }
    })
  } catch (e) {
    console.warn('[contentCache] 按前缀清理降级缓存失败', e)
  }

  return removed
}
