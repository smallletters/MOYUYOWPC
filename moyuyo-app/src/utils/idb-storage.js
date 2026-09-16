/**
 * IndexedDB 大文件存储工具
 *
 * 用途:GLB/FBX 等 3D 模型文件通常几 MB ~ 几十 MB,localStorage 5MB 上限不够用,
 *      IndexedDB 容量按域名分配,通常 50% 磁盘空间,远超需求。
 *
 * 用法:
 *   await idbPut('pet:model:abc', blob, { name: 'puppy.glb', format: 'glb' })
 *   const file = await idbGet('pet:model:abc')  // { blob, meta }
 *   await idbDelete('pet:model:abc')
 *   await idbClear()                            // 清空整个 store
 *   await idbKeys()                             // 列出所有 key
 *
 * 设计:
 * - 单库 moyuyo-idb, 单 object store `files`(keyPath 主键 = key 字符串)
 * - value = { blob, meta }  (meta 用于存储文件名、格式、创建时间)
 * - 所有方法都返回 Promise;不抛同步异常
 * - 不可用环境(如小程序 webview 关闭了 IDB)返回 rejected 让上层降级
 */

const DB_NAME = 'moyuyo-idb'
const DB_VERSION = 1
const STORE_NAME = 'files'

let dbPromise = null

/**
 * 打开 / 复用数据库连接
 */
function openDB() {
  if (dbPromise) return dbPromise
  // 不在浏览器环境(小程序)直接拒
  if (typeof indexedDB === 'undefined') {
    dbPromise = Promise.reject(new Error('IndexedDB not supported in this environment'))
    return dbPromise
  }
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        // keyPath 模式,主键直接来自 value.key
        db.createObjectStore(STORE_NAME, { keyPath: 'key' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error || new Error('openDB failed'))
  })
  // 连接异常后下次重新尝试
  dbPromise.catch(() => {
    dbPromise = null
  })
  return dbPromise
}

/**
 * 通用事务包装:storeName + mode 决定读写
 */
function tx(mode, fn) {
  return openDB().then(
    (db) =>
      new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, mode)
        const store = transaction.objectStore(STORE_NAME)
        let result
        try {
          result = fn(store)
        } catch (e) {
          reject(e)
          return
        }
        // 事务完成后 resolve; 异常 reject
        transaction.oncomplete = () =>
          resolve(result && typeof result.onsuccess !== 'undefined' ? null : result)
        transaction.onerror = () => reject(transaction.error)
        transaction.onabort = () => reject(transaction.error || new Error('transaction aborted'))
      }),
  )
}

/**
 * 写入 / 覆盖一条记录
 * @param {string} key
 * @param {Blob} blob
 * @param {object} [meta] 任意可序列化元数据
 */
export function idbPut(key, blob, meta = {}) {
  if (!key) return Promise.reject(new Error('idbPut: key required'))
  if (!(blob instanceof Blob)) {
    // 兼容传入 ArrayBuffer / Uint8Array 的场景
    if (blob instanceof ArrayBuffer || ArrayBuffer.isView(blob)) {
      // eslint-disable-next-line no-param-reassign
      blob = new Blob([blob])
    } else {
      return Promise.reject(new Error('idbPut: blob must be Blob / ArrayBuffer'))
    }
  }
  return tx('readwrite', (store) => {
    return new Promise((resolve, reject) => {
      const record = {
        key,
        blob,
        meta: { ...meta, size: blob.size, type: blob.type || '', updatedAt: Date.now() },
      }
      const req = store.put(record)
      req.onsuccess = () => resolve(record)
      req.onerror = () => reject(req.error)
    })
  })
}

/**
 * 读取一条记录,返回 { key, blob, meta } 或 null
 */
export function idbGet(key) {
  return tx('readonly', (store) => {
    return new Promise((resolve, reject) => {
      const req = store.get(key)
      req.onsuccess = () => resolve(req.result || null)
      req.onerror = () => reject(req.error)
    })
  })
}

/**
 * 删除一条记录
 */
export function idbDelete(key) {
  return tx('readwrite', (store) => {
    return new Promise((resolve, reject) => {
      const req = store.delete(key)
      req.onsuccess = () => resolve(true)
      req.onerror = () => reject(req.error)
    })
  })
}

/**
 * 列出所有 key(同步遍历 cursor 收齐后返回)
 */
export function idbKeys() {
  return tx('readonly', (store) => {
    return new Promise((resolve, reject) => {
      const keys = []
      const req = store.openKeyCursor()
      req.onsuccess = () => {
        const cursor = req.result
        if (cursor) {
          keys.push(cursor.key)
          cursor.continue()
        } else {
          resolve(keys)
        }
      }
      req.onerror = () => reject(req.error)
    })
  })
}

/**
 * 清空整个 object store(慎用)
 */
export function idbClear() {
  return tx('readwrite', (store) => {
    return new Promise((resolve, reject) => {
      const req = store.clear()
      req.onsuccess = () => resolve(true)
      req.onerror = () => reject(req.error)
    })
  })
}

// ============== Blob URL 缓存 ==============
// 同一 key 多次 get 时复用同一个 blob URL,避免每次都 URL.createObjectURL 造成内存泄漏
const urlCache = new Map() // key -> { url, refCount, blob }

function revokeIfOrphan(url) {
  // refCount <= 0 才回收
  for (const [k, v] of urlCache) {
    if (v.url === url && v.refCount <= 0) {
      URL.revokeObjectURL(url)
      urlCache.delete(k)
    }
  }
}

/**
 * 获取一个 key 对应的 blob URL(自动管理 URL.createObjectURL 生命周期)
 * 如果该 key 已有 URL,refCount + 1 并返回;否则从 IDB 读 blob 后创建 URL
 *
 * @returns Promise<{ url: string, meta: object } | null>
 */
export async function idbGetBlobURL(key) {
  const cached = urlCache.get(key)
  if (cached) {
    cached.refCount += 1
    return { url: cached.url, meta: cached.meta }
  }
  const rec = await idbGet(key)
  if (!rec) return null
  const url = URL.createObjectURL(rec.blob)
  urlCache.set(key, { url, refCount: 1, blob: rec.blob, meta: rec.meta })
  return { url, meta: rec.meta }
}

/**
 * 释放一个 blob URL(实际是 refCount - 1,降到 0 才 revoke)
 */
export function idbReleaseBlobURL(key) {
  const cached = urlCache.get(key)
  if (!cached) return
  cached.refCount = Math.max(0, cached.refCount - 1)
  if (cached.refCount === 0) {
    URL.revokeObjectURL(cached.url)
    urlCache.delete(key)
  }
}

/**
 * 删除 key 时,同时清掉已缓存的 blob URL
 */
export async function idbDeleteWithURL(key) {
  const cached = urlCache.get(key)
  if (cached) {
    URL.revokeObjectURL(cached.url)
    urlCache.delete(key)
  }
  return idbDelete(key)
}

/**
 * 工具:估算 IDB 已用空间(navigator.storage.estimate,不支持时返回 null)
 */
export async function idbEstimateUsage() {
  if (typeof navigator === 'undefined' || !navigator.storage || !navigator.storage.estimate) {
    return null
  }
  try {
    const r = await navigator.storage.estimate()
    return r
  } catch (e) {
    return null
  }
}
