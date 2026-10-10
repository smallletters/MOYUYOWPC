/**
 * 今日活跃心跳工具：把"是否上报今日活跃"封装为单一入口，
 * 给 App.vue onShow / home.vue onShow / 其他需要的位置调用，
 * 避免在多处复制去重逻辑造成不一致。
 *
 * 设计：三重去重，保证 onShow 高频触发也不会造成浪费。
 * <ol>
 *   <li>客户端预检：本地 dateKey 已等于今日 → 直接 short-circuit，不发请求</li>
 *   <li>服务端 Redis SETNX：即便客户端缓存丢失/跨设备，服务端也保证同 userId+dateKey 一日只写一次 DB</li>
 *   <li>失败兜底：请求不论成败都把服务端给的 dateKey 写本地，
 *       下一次 onShow 客户端预检直接拦截，避免反复失败反复请求</li>
 * </ol>
 * 未登录直接 short-circuit；异常全部吞掉不影响调用方主流程。
 *
 * 同一用户在多设备登录：每次心跳只是 UPDATE 同一 userId 行的 last_login_time，
 * onlineUsers 用 COUNT(*) 统计 mo_user 行数，物理上去重，多设备不会变成多用户。
 */

import { heartbeat as heartbeatApi } from '@/api/user'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'

const LOG_TAG = '[heartbeat]'

/**
 * 返回本地今日 yyyyMMdd (与后端 HeartbeatAck.dateKey 对齐)。
 * 使用本地时区，与后端的 LocalDateTime 行为一致。
 */
function todayDateKey() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

/**
 * 上报"今日活跃"心跳。
 * <p>
 * 安全可重入：无副作用失败不会抛错，未登录直接返回。
 * <p>
 * 与 home.vue 旧版 reportActiveHeartbeat 行为完全一致；
 * 后续 App.vue onShow 也复用此函数，让"切到任意 tab"和"从后台切回"
 * 都能让 last_login_time 保持新鲜，喂饱后端实时在线用户 30 分钟阈值。
 */
export function reportActiveHeartbeat() {
  try {
    // 1) 未登录(token 为空)直接返回,不浪费一次请求
    const token = getStorage(STORAGE_KEYS.TOKEN)
    if (!token) return

    // 2) 客户端预检:本地 dateKey 与今日一致 → 同一日重复切首页不发请求
    //    跨日时本地仍是昨天/前天的 dateKey,与今日不同 → 自然会发请求完成"补打"
    const localDateKey = getStorage(STORAGE_KEYS.HEARTBEAT_DATE_KEY)
    const today = todayDateKey()
    if (localDateKey === today) return

    // 3) 真正调接口,服务端 SETNX 再兜底一次
    heartbeatApi()
      .then((res) => {
        // 后端约定:Result.success 包装,data = { dateKey, nowMillis }
        const payload = res && (res.data || res)
        const serverDateKey = payload && payload.dateKey
        // 拿到服务端 dateKey 后立刻写本地(不论是否与 localDateKey 相同)
        // 这样下一次 onShow 触发就能命中客户端预检直接 short-circuit
        if (serverDateKey) {
          setStorage(STORAGE_KEYS.HEARTBEAT_DATE_KEY, serverDateKey)
        }
      })
      .catch(() => {
        // 心跳失败不能影响调用方,吞掉(401 由 request.js 弹 modal,4xx/5xx 已 _silent)
        // 不写本地 dateKey → 下次 onShow 仍会重试,Redis SETNX 保证服务端仍幂等
      })
  } catch (e) {
    // 整个心跳流程任何同步异常都不能阻断调用方
    console.warn(`${LOG_TAG} failed:`, e)
  }
}
