/**
 * 私信 / 群聊 / 客服消息本地缓存
 *
 * 设计目标：
 * 1. 聊天消息本地优先：打开会话先渲染缓存，再向云端增量同步
 * 2. 云端只保留固定天数（默认 30 天），本地缓存同样按 30 天裁剪，避免无限增长
 * 3. 多账号隔离：key 带 userId，退出登录时按用户清理，避免串号
 * 4. 优先 IndexedDB（容量大）；不支持的环境自动降级到 uni Storage（单会话保留条数更少）
 *
 * IndexedDB 记录结构：
 *   key = `${scope}:${userId}:${conversationId}`
 *   value = { key, scope, userId, conversationId, messages: Array, updatedAt }
 * 降级存储 key：
 *   `moyuyo_msg_cache:${scope}:${userId}:${conversationId}`
 */

import { getStorage, setStorage, removeStorage } from '@/utils/storage'

const DB_NAME = 'moyuyo-msg'
const DB_VERSION = 1
const STORE_NAME = 'conversation_messages'
const INDEX_USER = 'by_user'

// IndexedDB 下单会话最多缓存的条数：防止极端长会话把本地存储撑爆
const MAX_MESSAGES_PER_CONVERSATION = 2000
// 降级到 uni Storage 时的条数上限：单 key 容量有限（小程序 1MB / APP 约 10MB）
const FALLBACK_MAX_MESSAGES = 200
// 降级存储 key 前缀
const FALLBACK_PREFIX = 'moyuyo_msg_cache'
// 默认保留天数，与后端清理周期保持一致
export const MESSAGE_CACHE_RETENTION_DAYS = 30

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
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'key' })
        // 按用户清理缓存时用索引，避免全表遍历
        store.createIndex(INDEX_USER, 'userId', { unique: false })
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
    console.warn('[messageCache] IndexedDB 不可用，降级为 uni Storage：', error && error.message)
  }
  idbBroken = true
}

/** 会话缓存 key：scope 区分私信/客服，userId 隔离账号 */
function buildKey(scope, userId, conversationId) {
  return `${scope}:${userId}:${conversationId}`
}

/** 降级存储 key */
function fallbackKey(scope, userId, conversationId) {
  return `${FALLBACK_PREFIX}:${scope}:${userId}:${conversationId}`
}

/** 按保留天数裁剪，并限制单会话最大条数（保留最新部分） */
function pruneMessages(list, retentionDays, maxCount = MAX_MESSAGES_PER_CONVERSATION) {
  const days = Number(retentionDays) > 0 ? Number(retentionDays) : MESSAGE_CACHE_RETENTION_DAYS
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000
  const kept = (Array.isArray(list) ? list : []).filter((m) => {
    // 私信页面用 time，客服消息沿用后端 createTime，两种字段都兼容
    const t = new Date((m && (m.time || m.createTime)) || 0).getTime()
    // 时间无效的消息（极少数脏数据）直接丢弃，避免永久残留
    return Number.isFinite(t) && t >= cutoff
  })
  if (kept.length <= maxCount) return kept
  return kept.slice(kept.length - maxCount)
}

/** 转成可结构化克隆的纯数据：Vue 响应式代理直接写 IndexedDB 会抛 DataCloneError */
function serializeMessages(messages) {
  try {
    return JSON.parse(JSON.stringify(messages || []))
  } catch (e) {
    return Array.isArray(messages) ? messages.slice() : []
  }
}

/** IndexedDB 读取 */
async function loadFromIDB({ scope, userId, conversationId, retentionDays }) {
  // withStore 在事务 complete 后返回 handler 的返回值（此处为 IDBRequest），此时 result 已可用
  const req = await withStore('readonly', (store) =>
    store.get(buildKey(scope, userId, conversationId)),
  )
  const raw = (req && req.result && req.result.messages) || []
  const pruned = pruneMessages(raw, retentionDays)
  // 缓存里有过期消息时顺手写回，避免每次打开都重复过滤
  if (pruned.length !== raw.length) {
    saveToIDB({
      scope,
      userId,
      conversationId,
      messages: pruned,
      retentionDays,
    }).catch(() => {})
  }
  return pruned
}

/** IndexedDB 写入 */
async function saveToIDB({ scope, userId, conversationId, messages, retentionDays }) {
  const list = pruneMessages(messages, retentionDays)
  await withStore('readwrite', (store) => {
    store.put({
      key: buildKey(scope, userId, conversationId),
      scope,
      userId: String(userId),
      conversationId: String(conversationId),
      messages: list,
      updatedAt: Date.now(),
    })
    return true
  })
}

/** 降级读取：uni Storage 同步读写，超期数据顺手回收 */
function loadFromFallback({ scope, userId, conversationId, retentionDays }) {
  const key = fallbackKey(scope, userId, conversationId)
  const raw = getStorage(key, [])
  const list = Array.isArray(raw) ? raw : []
  const pruned = pruneMessages(list, retentionDays, FALLBACK_MAX_MESSAGES)
  if (pruned.length !== list.length) setStorage(key, pruned)
  return pruned
}

/**
 * 读取会话缓存
 * @param {{scope:string,userId:string,conversationId:string,retentionDays?:number}} params
 * @returns {Promise<Array>} 按时间正序的消息数组，失败或无缓存返回 []
 */
export async function loadCachedMessages(params) {
  const { scope, userId, conversationId, retentionDays } = params || {}
  if (!scope || !userId || !conversationId) return []
  if (!idbBroken) {
    try {
      return await loadFromIDB({ scope, userId, conversationId, retentionDays })
    } catch (e) {
      markIdbBroken(e)
    }
  }
  return loadFromFallback({ scope, userId, conversationId, retentionDays })
}

/**
 * 保存会话缓存（整体覆盖）
 * @param {{scope:string,userId:string,conversationId:string,messages:Array,retentionDays?:number}} params
 * @returns {Promise<boolean>}
 */
export async function saveCachedMessages(params) {
  const { scope, userId, conversationId, retentionDays } = params || {}
  if (!scope || !userId || !conversationId) return false
  const serialized = serializeMessages(params.messages)
  if (!idbBroken) {
    try {
      await saveToIDB({ scope, userId, conversationId, messages: serialized, retentionDays })
      return true
    } catch (e) {
      markIdbBroken(e)
    }
  }
  // 降级：只保留最近 N 条，避免单 key 超限
  const list = pruneMessages(serialized, retentionDays, FALLBACK_MAX_MESSAGES)
  return setStorage(fallbackKey(scope, userId, conversationId), list)
}

/** 删除单个会话缓存（删除会话、关闭客服会话时调用） */
export async function deleteCachedConversation(params) {
  const { scope, userId, conversationId } = params || {}
  if (!scope || !userId || !conversationId) return false
  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        store.delete(buildKey(scope, userId, conversationId))
        return true
      })
      return true
    } catch (e) {
      markIdbBroken(e)
    }
  }
  return removeStorage(fallbackKey(scope, userId, conversationId))
}

/** 清理某个用户的全部消息缓存（退出登录时调用，避免账号串号） */
export async function clearUserMessageCache(userId) {
  if (!userId) return false
  let cleared = false
  if (!idbBroken) {
    try {
      await withStore('readwrite', (store) => {
        const index = store.index(INDEX_USER)
        const req = index.openKeyCursor(String(userId))
        req.onsuccess = () => {
          const cursor = req.result
          if (!cursor) return
          store.delete(cursor.primaryKey)
          cursor.continue()
        }
        return true
      })
      cleared = true
    } catch (e) {
      markIdbBroken(e)
    }
  }
  // 同时清理降级存储（覆盖曾经降级写入的数据），按 key 中的 userId 精确匹配
  try {
    const info = uni.getStorageInfoSync()
    const marker = `:${userId}:`
    ;((info && info.keys) || []).forEach((key) => {
      if (key.startsWith(`${FALLBACK_PREFIX}:`) && key.includes(marker)) removeStorage(key)
    })
    cleared = true
  } catch (e) {
    console.warn('[messageCache] 清理降级缓存失败', e)
  }
  return cleared
}
