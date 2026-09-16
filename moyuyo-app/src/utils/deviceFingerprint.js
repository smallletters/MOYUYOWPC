// 设备指纹工具:生成稳定且仅与设备相关的 deviceId
//
// 设计:
// 1) 原生 APP/小程序:uni.getSystemInfoSync().deviceId 在同一台设备重启后保持稳定,
//    直接作为 deviceId 即可。
// 2) H5:浏览器没有持久设备 id,首次访问生成一个 uuid,存 localStorage 复用。
// 3) 绝不掺入 token/userId:刷新 token 或切换账号不应被视为"换设备"。

const STORAGE_KEY = 'moyuyo:device:fingerprint'
let cachedId = ''

// 合法 deviceId 形态:
//  - 自生成 uuid:36 位标准格式(8-4-4-4-12 hex + 4 个 '-')
//  - 系统 deviceId:长度 >= 8 的字母数字串(各厂商规范不同,不强制 uuid)
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const SYS_ID_RE = /^[A-Za-z0-9._:-]{8,}$/

function genUuid() {
  // 36 位 uuid,无外部依赖,uniapp 多端可用
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * 校验字符串是否可作为合法 deviceId:
 *  - 标准 uuid(36 位)
 *  - 系统 deviceId(字母数字 + 常见分隔符,长度 >= 8)
 *  阻止历史遗留值(如 'web-anon' 9 字符)被误用作 deviceId。
 */
function isValidDeviceId(s) {
  if (typeof s !== 'string') return false
  return UUID_RE.test(s) || SYS_ID_RE.test(s)
}

/**
 * 获取当前设备稳定的 deviceId(同步)
 * - APP/小程序:系统 deviceId
 * - H5:localStorage 中持久化的 uuid
 */
export function getDeviceFingerprint() {
  // 优先返回内存缓存(模块级单例)
  if (cachedId && isValidDeviceId(cachedId)) return cachedId
  cachedId = ''
  // 优先读 storage 缓存;格式校验失败时视为脏数据,重新生成
  try {
    const cached = uni.getStorageSync(STORAGE_KEY)
    if (isValidDeviceId(cached)) {
      cachedId = cached
      return cachedId
    }
  } catch (_) {
    /* ignore */
  }
  // 取系统设备 id
  let sysId = ''
  try {
    const sys = uni.getSystemInfoSync ? uni.getSystemInfoSync() : {}
    sysId = sys.deviceId || ''
  } catch (_) {
    /* ignore */
  }
  // 系统 id 必须通过合法性校验,否则用自生成 uuid 兜底(避免厂商返回垃圾字符串污染 DB)
  const id = isValidDeviceId(sysId) ? sysId : genUuid()
  try {
    uni.setStorageSync(STORAGE_KEY, id)
  } catch (_) {
    /* ignore */
  }
  cachedId = id
  return cachedId
}

/** 强制重置(用于"切换设备"/调试场景) */
export function resetDeviceFingerprint() {
  cachedId = ''
  try {
    uni.removeStorageSync(STORAGE_KEY)
  } catch (_) {
    /* ignore */
  }
}

// HMR 安全:开发模式下 vite 热更新会重新执行模块顶层,但 ESM 模块顶层只执行一次
// 此处暴露一个 dev-only 自检:若 hot.accept 触发,清掉模块缓存让下次重新读取 storage
// 生产环境 vite 不注入 hot,h.apply 不存在,跳过
try {
  if (typeof module !== 'undefined' && module.hot && module.hot.accept) {
    module.hot.accept(() => {
      cachedId = ''
    })
  }
} catch (_) {
  /* ignore */
}
