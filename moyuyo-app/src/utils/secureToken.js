/**
 * 凭证加密存储适配层（accessToken + refreshToken）
 *
 * 为什么需要这一层：
 * 1. 原生插件只能异步回调，而业务侧有多处同步读取，因此用内存缓存对上层暴露同步读
 * 2. 需要兼容三种环境：已集成插件（加密）/ 未集成插件或 H5（明文降级）/ 老版本升级（明文迁移）
 * 3. 加密失败时必须降级为明文，绝不能因为安全加固导致用户无法登录
 *
 * 冷启动时序（关键）：
 * - Pinia 在 onLaunch 中创建，此时解密还没完成，内存缓存为空
 * - 因此额外维护一个「会话存在」明文标记（不含任何凭证），让 isLoggedIn 在解密完成前保持 true
 * - 所有网络请求在发出前 await ready()，确保不会带着空 token 打出 401
 */
import { readSecureItem, writeSecureItem, removeSecureItem } from '@/plugins/secureStorage'
import { getStorage, setStorage, removeStorage } from './storage'

/** 老版本明文键 */
const LEGACY_ACCESS_KEY = 'moyuyo_token'
const LEGACY_REFRESH_KEY = 'moyuyo_refresh_token'
/** 加密存储键（Keychain account / SharedPreferences key） */
const SECURE_ACCESS_KEY = 'moyuyo_access_token'
const SECURE_REFRESH_KEY = 'moyuyo_refresh_token'
/** 会话存在标记：仅记录"本机是否登录过"，不含任何凭证，用于规避冷启动解密窗口 */
const SESSION_FLAG_KEY = 'moyuyo_session_present'

/** 内存缓存：同步读入口，避免业务侧改造成异步 */
let cachedAccessToken = ''
let cachedRefreshToken = ''
/** 加密链路是否可用（插件缺失时为 false，走明文降级） */
let secureEnabled = false
/** 初始化单例，避免并发首次调用重复解密 */
let initPromise = null

/**
 * 是否要求凭证必须加密落盘。
 * <p>
 * APP 端：插件不可用时拒绝明文写入（只保留内存会话，重启后需重新登录），
 * 避免 refresh token 以明文形式暴露在设备存储/系统备份中。
 * H5/小程序：没有原生 Keychain/Keystore，保持原有 localStorage 行为。
 */
function isAppPlatform() {
  let flag = false
  // #ifdef APP-PLUS
  flag = true
  // #endif
  return flag
}

const ENFORCE_SECURE_STORAGE = isAppPlatform()

function readLegacy(key) {
  try {
    return getStorage(key, '') || ''
  } catch (e) {
    return ''
  }
}

function writeLegacy(key, value) {
  try {
    setStorage(key, value)
  } catch (e) {
    console.warn('[secureToken] 明文降级写入失败:', key, e?.message)
  }
}

function removeLegacy(key) {
  try {
    removeStorage(key)
  } catch (e) {
    /* 清理失败不阻断登出 */
  }
}

function markSessionPresent(present) {
  try {
    if (present) setStorage(SESSION_FLAG_KEY, '1')
    else removeStorage(SESSION_FLAG_KEY)
  } catch (e) {
    /* 标记失败只影响冷启动首帧的登录态展示，不影响凭证可用性 */
  }
}

/**
 * 单个凭证的初始化：优先密文，其次把老明文加密迁移
 * @param {string} secureKey 加密存储键
 * @param {string} legacyKey 老版本明文键
 * @param {{success: boolean, value: string}} stored 已读取的密文结果（避免重复调用插件）
 * @returns {Promise<string>} 最终可用的凭证值
 */
async function initOne(secureKey, legacyKey, stored) {
  const legacy = readLegacy(legacyKey)
  if (!stored.success) {
    // 插件不可用（H5 / 未打自定义基座）或读取失败：以明文为准。
    // 同时尽力回写密文：否则"读取失败但写入可用"的场景下，下次启动会读到旧密文并删掉新明文
    if (legacy) {
      if (await writeSecureItem(secureKey, legacy)) {
        removeLegacy(legacyKey)
      } else if (ENFORCE_SECURE_STORAGE) {
        // APP 端不允许明文落盘：清掉遗留明文，本次仅在内存中保留（重启后重新登录）
        console.error('[secureToken] 加密存储不可用，已清除明文凭证，重启后需重新登录')
        removeLegacy(legacyKey)
      }
    }
    return legacy
  }
  if (stored.value) {
    // 加密已生效：清掉升级前遗留的明文副本
    if (legacy) removeLegacy(legacyKey)
    return stored.value
  }
  if (!legacy) return ''
  // 密文里没有值，但本地还有明文 → 老版本升级迁移
  const ok = await writeSecureItem(secureKey, legacy)
  if (ok || ENFORCE_SECURE_STORAGE) {
    removeLegacy(legacyKey)
    if (!ok) {
      console.error('[secureToken] 迁移加密失败，已清除明文凭证，重启后需重新登录')
    }
  } else {
    console.warn('[secureToken] 迁移加密失败，暂保留明文:', legacyKey)
  }
  return legacy
}

/**
 * 初始化加密存储（App.vue onLaunch 调用一次，各请求入口也会 await）
 * <p>
 * 永远 resolve，不抛异常：解密失败一律降级明文，保证登录功能可用。
 * @returns {Promise<void>}
 */
export function initSecureTokens() {
  if (initPromise) return initPromise
  initPromise = (async () => {
    const [accessRes, refreshRes] = await Promise.all([
      readSecureItem(SECURE_ACCESS_KEY),
      readSecureItem(SECURE_REFRESH_KEY),
    ])
    // 至少一个键读取成功即认为加密链路可用（另一个键可能单独损坏）
    secureEnabled = accessRes.success || refreshRes.success

    cachedAccessToken = await initOne(SECURE_ACCESS_KEY, LEGACY_ACCESS_KEY, accessRes)
    cachedRefreshToken = await initOne(SECURE_REFRESH_KEY, LEGACY_REFRESH_KEY, refreshRes)

    if (cachedAccessToken || cachedRefreshToken) markSessionPresent(true)
  })().catch((e) => {
    console.warn('[secureToken] init failed, fallback to plaintext:', e?.message)
    secureEnabled = false
    cachedAccessToken = readLegacy(LEGACY_ACCESS_KEY)
    cachedRefreshToken = readLegacy(LEGACY_REFRESH_KEY)
    // APP 端解密链路异常时，同样不允许明文凭证继续留在设备上
    if (ENFORCE_SECURE_STORAGE) {
      removeLegacy(LEGACY_ACCESS_KEY)
      removeLegacy(LEGACY_REFRESH_KEY)
    }
  })
  return initPromise
}

/** 等待解密完成（请求发出前调用，避免带空 token 打 401） */
export function ready() {
  return initSecureTokens()
}

/** 同步读取 accessToken（替代 getStorage('moyuyo_token')） */
export function peekAccessToken() {
  return cachedAccessToken
}

/** 同步读取 refreshToken（替代 getStorage('moyuyo_refresh_token')） */
export function peekRefreshToken() {
  return cachedRefreshToken
}

/** 本机是否存在登录会话（明文标记，不含凭证） */
export function hasSessionHint() {
  try {
    return getStorage(SESSION_FLAG_KEY, '') === '1'
  } catch (e) {
    return false
  }
}

/** 写入单个凭证：优先加密，失败降级明文 */
async function persistOne(secureKey, legacyKey, value) {
  if (!value) {
    cachedSet(secureKey, '')
    removeLegacy(legacyKey)
    // 不依赖 secureEnabled：插件暂时不可用时也要尝试删除，否则恢复后旧凭证会"复活"
    await removeSecureItem(secureKey)
    return
  }
  cachedSet(secureKey, value)
  if (secureEnabled) {
    const ok = await writeSecureItem(secureKey, value)
    if (ok) {
      removeLegacy(legacyKey)
      return
    }
    console.warn('[secureToken] 加密写入失败，降级为明文:', legacyKey)
  }
  // 关键：必须清掉旧密文。否则下次启动 init 会优先读旧密文，并删除刚写入的明文，
  // 表现为"刚登录完重启就退回上一个会话"
  await removeSecureItem(secureKey)
  if (ENFORCE_SECURE_STORAGE) {
    // APP 端加密链路不可用时拒绝明文落盘：内存中仍可用，重启后需重新登录
    console.error('[secureToken] 加密写入失败，已拒绝明文落盘:', legacyKey)
    return
  }
  writeLegacy(legacyKey, value)
}

/** 写内存缓存（按 secureKey 分发，避免调用方传错） */
function cachedSet(secureKey, value) {
  if (secureKey === SECURE_ACCESS_KEY) cachedAccessToken = value
  else if (secureKey === SECURE_REFRESH_KEY) cachedRefreshToken = value
}

/**
 * 保存凭证（登录 / 注册 / 续期统一入口）
 * <p>
 * accessToken / refreshToken 传 undefined 表示"不改动该项"，传空串表示删除。
 * @param {{accessToken?: string, refreshToken?: string}} tokens
 * @returns {Promise<void>}
 */
export async function saveTokens(tokens = {}) {
  await initSecureTokens()
  // 必须在 init 之后再写缓存：init 里的"明文迁移"会把旧值回填进缓存，
  // 先写缓存会被旧值覆盖，导致刚签发的新凭证丢失
  if (tokens.accessToken !== undefined) {
    await persistOne(SECURE_ACCESS_KEY, LEGACY_ACCESS_KEY, tokens.accessToken || '')
  }
  if (tokens.refreshToken !== undefined) {
    await persistOne(SECURE_REFRESH_KEY, LEGACY_REFRESH_KEY, tokens.refreshToken || '')
  }
  markSessionPresent(!!(cachedAccessToken || cachedRefreshToken))
}

/**
 * 清除全部凭证（登出 / 注销账号 / 刷新失败）
 * <p>
 * 内存、明文键、会话标记同步清理，保证调用方立即进入未登录态；
 * 加密项异步删除，失败也不影响本次登出（下次登录会覆盖）。
 * @returns {Promise<void>}
 */
export async function clearTokens() {
  cachedAccessToken = ''
  cachedRefreshToken = ''
  removeLegacy(LEGACY_ACCESS_KEY)
  removeLegacy(LEGACY_REFRESH_KEY)
  markSessionPresent(false)
  await initSecureTokens()
  // init 可能把密文里的旧值回填进缓存，这里二次清空
  cachedAccessToken = ''
  cachedRefreshToken = ''
  markSessionPresent(false)
  // 不依赖 secureEnabled：插件暂时不可用时也必须尝试删除，否则恢复后旧凭证会"复活"
  await Promise.all([removeSecureItem(SECURE_ACCESS_KEY), removeSecureItem(SECURE_REFRESH_KEY)])
}
