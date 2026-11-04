/**
 * 私信 WebSocket 客户端
 *
 * 连接地址：ws(s)://<后端地址>/ws/dm?token=<JWT>
 * - 自动重连（指数退避，最长 30s）
 * - 25s 心跳，避免代理层回收空闲连接
 * - 单例连接，多页面共享；页面通过 onDmSocketEvent 订阅消息
 */
import { config } from '@/utils/config'
import { getStorage, STORAGE_KEYS } from '@/utils/storage'

/** 消息 Tab 在 tabBar 中的索引（pages.json 顺序：首页/宠物/社区/消息/我的） */
export const MESSAGE_TAB_INDEX = 3

const HEARTBEAT_MS = 25 * 1000
const RETRY_MIN_MS = 3 * 1000
const RETRY_MAX_MS = 30 * 1000

let socketTask = null
let connecting = false
let manualClosed = false
let retryTimer = null
let retryDelay = RETRY_MIN_MS
let heartbeatTimer = null
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

/** 拼接带 token 的连接地址 */
function buildSocketUrl() {
  const token = getStorage(STORAGE_KEYS.TOKEN, '')
  if (!token) return ''
  const base = resolveWsBase()
  if (!base) return ''
  return `${base.replace(/\/$/, '')}/ws/dm?token=${encodeURIComponent(token)}`
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
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

function scheduleReconnect() {
  if (manualClosed || retryTimer) return
  if (!getStorage(STORAGE_KEYS.TOKEN, '')) return
  retryTimer = setTimeout(() => {
    retryTimer = null
    connectDmSocket()
  }, retryDelay)
  retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS)
}

/** 建立连接（幂等；已连接或连接中直接返回） */
export function connectDmSocket() {
  if (socketTask || connecting) return
  const url = buildSocketUrl()
  if (!url) return
  manualClosed = false
  connecting = true

  const task = uni.connectSocket({
    url,
    complete: () => {},
  })
  socketTask = task

  task.onOpen(() => {
    if (socketTask !== task) return
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

  task.onClose(() => {
    if (socketTask !== task) return
    socketTask = null
    connecting = false
    stopHeartbeat()
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
