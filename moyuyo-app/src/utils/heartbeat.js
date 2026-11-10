/**
 * 今日活跃心跳工具：把"上报活跃"封装为单一入口，
 * 给 App.vue onShow / 前台定时器 / home.vue onShow 调用，
 * 避免在多处复制去重逻辑造成不一致。
 *
 * 设计：客户端节流 + 服务端去重。
 * <ol>
 *   <li>客户端节流：距上次上报不足 {@link HEARTBEAT_MIN_INTERVAL_MS} 直接 short-circuit，不发请求</li>
 *   <li>服务端每日幂等：Redis SETNX 保证同 userId 一天只写一次 mo_user.last_login_time（每日活跃统计）</li>
 *   <li>服务端在线窗口：每次心跳都会刷新 Redis ZSET 中该用户的"最近活跃时间"，
 *       管理后台实时在线人数按最近 30 分钟活跃统计，不再依赖每天只写一次的 last_login_time</li>
 * </ol>
 * 未登录直接 short-circuit；异常全部吞掉不影响调用方主流程。
 *
 * 同一用户在多设备登录：在线人数按 userId 去重（ZSET member 唯一），多设备不会变成多个用户。
 */

import { heartbeat as heartbeatApi } from '@/api/user'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'
import { peekAccessToken } from '@/utils/secureToken'

const LOG_TAG = '[heartbeat]'

/**
 * 最小上报间隔：5 分钟。
 * 必须明显小于服务端 30 分钟在线窗口，否则用户在线期间会被误判为离线；
 * 又不能太短，避免大量在线客户端的定时器把 /auth/heartbeat 打成高频接口。
 */
const HEARTBEAT_MIN_INTERVAL_MS = 5 * 60 * 1000

/**
 * 上报活跃心跳。
 * <p>
 * 安全可重入：无副作用失败不会抛错，未登录直接返回。
 * <p>
 * 与 onShow 高频触发解耦：本地时间戳节流，5 分钟内重复调用不会发请求。
 */
export function reportActiveHeartbeat() {
  try {
    // 1) 未登录(token 为空)直接返回,不浪费一次请求
    const token = peekAccessToken()
    if (!token) return

    // 2) 客户端节流:距上次上报不足 5 分钟直接跳过
    const now = Date.now()
    const lastAt = Number(getStorage(STORAGE_KEYS.HEARTBEAT_LAST_AT, 0)) || 0
    if (now - lastAt < HEARTBEAT_MIN_INTERVAL_MS) return
    // 关键:先写时间戳再发请求。失败也计入节流窗口 —— 断网/服务端抖动时
    // 不会因为每次 onShow 都重试而放大请求；30 分钟在线窗口足够容忍漏报几次
    setStorage(STORAGE_KEYS.HEARTBEAT_LAST_AT, now)

    // 3) 真正调接口,服务端负责每日幂等 + 在线窗口刷新
    heartbeatApi().catch(() => {
      // 心跳失败不能影响调用方,吞掉(401 由 request.js 弹 modal,4xx/5xx 已 _silent)
    })
  } catch (e) {
    // 整个心跳流程任何同步异常都不能阻断调用方
    console.warn(`${LOG_TAG} failed:`, e)
  }
}
