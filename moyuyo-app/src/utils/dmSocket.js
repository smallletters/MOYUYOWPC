/**
 * 私信 WebSocket 客户端
 *
 * 连接地址：ws(s)://<后端地址>/ws/dm?ticket=<一次性票据>
 * - ticket 通过 REST /api/v1/auth/ws-ticket 换取，60 秒有效且只能使用一次
 * - 自动重连（指数退避，最长 30s）
 * - 25s 心跳，避免代理层回收空闲连接
 * - 单例连接，多页面共享；页面通过 onDmSocketEvent 订阅消息
 */
import { config } from '@/utils/config'
import { ready, peekAccessToken } from '@/utils/secureToken'
import { getWsTicket } from '@/api/user'

/** 消息 Tab 在 tabBar 中的索引（pages.json 顺序：首页/宠物/社区/消息/我的） */
export const MESSAGE_TAB_INDEX = 3

const HEARTBEAT_MS = 25 * 1000
const RETRY_MIN_MS = 3 * 1000
const RETRY_MAX_MS = 30 * 1000
/** 握手超时：超时未 onOpen 主动断开并重连，避免平台不回调 onClose 时永久卡在"假在线" */
const CONNECT_TIMEOUT_MS = 10 * 1000
/** 与服务端 DmSessionRegistry.SESSION_INVALIDATED_CLOSE_CODE 保持一致 */
const SESSION_INVALIDATED_CLOSE_CODE = 4001

let socketTask = null
let connecting = false
let manualClosed = false
let retryTimer = null
let retryDelay = RETRY_MIN_MS
let heartbeatTimer = null
let connectTimer = null
const listeners = new Set()

/** WebSocket 根地址：优先环境变量，其次当前页面 origin（H5） */
function resolveWsBase() {
  const apiBase = (config && config.adminApiBase) || ''
  if (apiBase) {
    return apiBase.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:')
  }
  if (typeof window !== 'undefined' && window.location) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}`
  }
  return ''
}

/** 拼接带一次性 ticket 的连接地址 */
function buildSocketUrl(ticket) {
  if (!ticket) return ''
  const base = resolveWsBase()
  if (!base) return ''
  return `${base.replace(/\/$/, '')}/ws/dm?ticket=${encodeURIComponent(ticket)}`
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/** 清除握手超时计时器 */
function stopConnectTimer() {
  if (connectTimer) {
    clearTimeout(connectTimer)
    connectTimer = null
  }
}

function startHeartbeat(task) {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (!socketTask || socketTask !== task) return
    try {
      task.send({ data: '{"type":"ping"}' })
    } catch (e) {
      // 发送失败由 onError/onClose 触发重连
    }
  }, HEARTBEAT_MS)
}

/**
 * 下一次重连延时：指数退避 + ±20% 随机抖动。
 * 不加抖动时，服务端重启/发版后所有客户端会在同一时间窗口重连（惊群），
 * 且每次重连都要先换一次 ws-ticket，瞬间打满认证接口。
 */
function nextRetryDelay() {
  const jittered = retryDelay * (0.8 + Math.random() * 0.4)
  return Math.min(Math.max(jittered, RETRY_MIN_MS), RETRY_MAX_MS)
}

function scheduleReconnect() {
  if (manualClosed || retryTimer) return
  if (!peekAccessToken()) return
  const delay = nextRetryDelay()
  retryTimer = setTimeout(() => {
    retryTimer = null
    connectDmSocket()
  }, delay)
  retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS)
}

/** 建立连接（幂等；已连接或连接中直接返回） */
export async function connectDmSocket() {
  if (socketTask || connecting) return
  // 先同步占位再 await：ready() 期间若并发调用 connectDmSocket，两个调用都会通过上面的
  // 幂等判断而重复建连（模块只保留最后一个 socketTask，前一个会泄漏且消息重复）
  connecting = true
  // 凭证解密完成后再判断登录态，避免冷启动时误判为未登录而不建连
  await ready()
  // 未登录不建立连接
  if (!peekAccessToken()) {
    connecting = false
    return
  }
  manualClosed = false

  // 先换取一次性 ticket，再建立连接；ticket 不落 URL 日志且一次性有效
  let ticket = ''
  try {
    const res = await getWsTicket()
    ticket = res && res.ticket ? res.ticket : ''
  } catch (e) {
    connecting = false
    if (!manualClosed) scheduleReconnect()
    return
  }
  if (manualClosed) {
    connecting = false
    return
  }
  const url = buildSocketUrl(ticket)
  if (!url) {
    connecting = false
    if (!manualClosed) scheduleReconnect()
    return
  }

  let task
  try {
    task = uni.connectSocket({
      url,
      complete: () => {},
    })
  } catch (e) {
    connecting = false
    if (!manualClosed) scheduleReconnect()
    return
  }
  socketTask = task

  // 握手超时保护：部分平台/代理下既不会回调 onOpen 也不会回调 onClose，
  // 不主动断开就会永久占位（socketTask 非空 → 后续 connect 全部幂等返回），
  // 表现为"假在线"：没有心跳、不会重连、也收不到任何消息
  stopConnectTimer()
  connectTimer = setTimeout(() => {
    connectTimer = null
    if (socketTask !== task) return
    socketTask = null
    connecting = false
    try {
      task.close({})
    } catch (e) {
      // 忽略关闭异常
    }
    scheduleReconnect()
  }, CONNECT_TIMEOUT_MS)

  task.onOpen(() => {
    if (socketTask !== task) return
    stopConnectTimer()
    connecting = false
    retryDelay = RETRY_MIN_MS
    startHeartbeat(task)
  })

  task.onMessage((res) => {
    if (socketTask !== task) return
    let payload = null
    try {
      payload = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    } catch (e) {
      // 非 JSON（心跳 pong 等）忽略
      return
    }
    if (!payload || payload.type === 'pong') return
    // 服务端改密后强制下线：停止自动重连，避免用已失效 token 反复握手
    if (payload.type === 'session_invalidated') {
      manualClosed = true
      return
    }
    listeners.forEach((fn) => {
      try {
        fn(payload)
      } catch (e) {
        console.warn('[dmSocket] listener error', e)
      }
    })
  })

  task.onError(() => {
    // onClose 会跟着触发，重连逻辑集中在 onClose
  })

  task.onClose((res) => {
    if (socketTask !== task) return
    socketTask = null
    connecting = false
    stopConnectTimer()
    stopHeartbeat()
    // 4001 = 服务端主动使 token 失效（改密/重置密码），此时不再自动重连
    if (res && res.code === SESSION_INVALIDATED_CLOSE_CODE) {
      manualClosed = true
      if (retryTimer) {
        clearTimeout(retryTimer)
        retryTimer = null
      }
      return
    }
    scheduleReconnect()
  })
}

/** 订阅私信事件，返回取消订阅函数 */
export function onDmSocketEvent(fn) {
  if (typeof fn !== 'function') return () => {}
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** 主动关闭连接（退出登录时调用），不再自动重连 */
export function closeDmSocket() {
  manualClosed = true
  stopHeartbeat()
  stopConnectTimer()
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (socketTask) {
    try {
      socketTask.close({})
    } catch (e) {
      // 忽略关闭异常
    }
    socketTask = null
  }
  connecting = false
}

/** 同步消息 Tab 未读角标（n <= 0 时移除） */
export function syncMessageTabBadge(total) {
  const n = Number(total || 0)
  try {
    if (n > 0) {
      uni.setTabBarBadge({
        index: MESSAGE_TAB_INDEX,
        text: n > 99 ? '99+' : String(n),
        fail: () => {},
      })
    } else {
      uni.removeTabBarBadge({ index: MESSAGE_TAB_INDEX, fail: () => {} })
    }
  } catch (e) {
    // 非 tabBar 页或平台不支持时忽略
  }
}
