<script>
import { useThemeStore } from '@/store'
import { useUserStore } from '@/store'
// 静态 import(替代原 import('@/utils/payAppBridge') 动态 import):
//   HBuilder(uni-app 3.8.12)在 App.vue 顶层出现 dynamic import 时,
//   会启用 Rollup 代码分割,但其 output.format 默认是 iife,二者冲突 → build failed.
//   静态 import 在各端都被正常打入(并被 tree-shake / 条件编译优化),
//   运行时通过 if(_schemeCleanup) 早退保证只在 APP 端真正注册监听。
import { registerMoyuyoScheme } from '@/utils/payAppBridge'
// 探活 forceLogout 后弹登录过期 modal 用
import { triggerSessionExpired } from '@/utils/request'
// 全局心跳：保证"用户在任意页面 → 后台切回 APP"都能刷新 last_login_time，
// 让管理后台 实时大屏 的 30 分钟在线窗口能持续计入。
// 复用 home.vue 的三重去重实现（本地 dateKey + 服务端 Redis SETNX + 失败兜底），
// 内部已经处理同日幂等，不会变成 N 次请求。
import { reportActiveHeartbeat } from '@/utils/heartbeat'

// APP 端全局 scheme 监听：
// 支付成功/取消后,Stripe Checkout / PayPal / 支付宝 APP 会用
//   moyuyo://pay/return?status=success&orderNo=xxx
// 回到你的 Moyuyo APP。全局监听：
//   1) 如果 pay 页在栈上,pay 页自己注册的监听会处理(更精确,不重复)
//   2) 如果 APP 已被系统回收 → 冷启动 → 全局监听到后直接跳到订单详情
let _schemeCleanup = null

/**
 * M6：onShow 探活节流（闭包变量，仅 App.vue 内部使用）
 * <p>
 * 用闭包而非 store 字段：避免与业务页面主动调用的 fetchProfile 耦合，
 * 节流只作用于"探活"这条特定路径（onLaunch / onShow），
 * 业务页面 onShow 主动调 fetchProfile 不受 30s 节流限制。
 */
let _lastProbeAt = 0
const PROBE_THROTTLE_MS = 30 * 1000
// 探活请求所属业务路径（用于日志区分 onShow 探活 vs 业务页面主动调用）
const PROBE_LOG_TAG = '[App.probe]'

/**
 * M4：onLaunch 完成标志
 * <p>
 * 防御 uni-app 在 onLaunch 完成前就触发 onShow 的极端场景（某些平台/低概率时序）：
 * onShow 内的 useUserStore() / useThemeStore() 依赖 pinia 初始化，
 * onLaunch 没跑完时 pinia store 可能未挂载。
 * <p>
 * onLaunch 末尾置 true；onShow 检查 false 则早退，等下次 onShow 再处理。
 */
let appLaunched = false

/**
 * 根据 fetchProfile 抛出的错误决定是否 forceLogout
 * <p>
 * 关键修复：网络错误（isNetworkError=true）和超时不能让用户掉登录！
 * 历史上 App.vue 的 onLaunch / onShow 在 fetchProfile 失败时无脑 forceLogout，
 * 配合 30 分钟后台切回时 OS 网络栈 keep-alive 失效的场景，会让"网络抖动 = 掉登录"。
 * <p>
 * 现在区分：
 * - 网络错误/超时：静默忽略（request.js 内部已重试一次，仍失败说明真没网）
 * - 真 Unauthorized：说明 refresh token 也失效了，必须 forceLogout
 * - 其他业务错误：用户 token 是有效的，只是后端临时错误，不应 forceLogout
 *
 * @param {Error} e fetchProfile 抛出的错误
 */
function handleProbeError(e) {
  if (!e) return
  if (e.isNetworkError) {
    // 网络错误：不掉登录，等下次网络恢复再由业务请求触发 refresh
    console.warn(`${PROBE_LOG_TAG} network error, skip forceLogout:`, e.message)
    return
  }
  if (e.message === 'Unauthorized') {
    // 真 401 且 refresh 也失败：掉登录（forceLogout 内部会停续期定时器）
    // 关键：用户必须感知被踢的原因，主动弹登录过期 modal
    console.warn(`${PROBE_LOG_TAG} unauthorized, forceLogout + prompt`)
    const userStore = useUserStore()
    userStore.forceLogout()
    triggerSessionExpired()
    return
  }
  // 其他业务错误（如 5xx、4xx）：token 仍有效，userInfo 用缓存兜底
  console.warn(`${PROBE_LOG_TAG} other error, keep login state:`, e.message)
}

/**
 * 探活核心函数：拉一次 /me 验证登录态，按错误类型决定是否踢用户
 * <p>
 * 抽出来的好处：
 * 1) onLaunch 和 onShow 复用同一份探活逻辑（避免代码重复）
 * 2) 节流、handleProbeError、forceLogout 提示都集中在一处，便于维护
 * 3) 与业务页面 onShow 主动 fetchProfile 完全解耦（节流只作用于本函数）
 *
 * @param {object} userStore useUserStore() 返回的 store 实例
 * @param {string} reason 调用方标识（如 'onLaunch' / 'onShow'），用于日志
 */
function tryProbe(userStore, reason) {
  if (!userStore.token) return
  // 探活节流：30 秒内的重复切回不重复发 /me
  // 业务页面 onShow 调 fetchProfile 不走本函数，节流不影响
  const now = Date.now()
  if (now - _lastProbeAt < PROBE_THROTTLE_MS) {
    console.log(`${PROBE_LOG_TAG}[${reason}] throttled, skip`)
    return
  }
  _lastProbeAt = now
  console.log(`${PROBE_LOG_TAG}[${reason}] probing...`)
  userStore.fetchProfile().catch(handleProbeError)
}

function ensureSchemeRegistered() {
  if (_schemeCleanup) return
  // 非 APP 端没有 plus.runtime,直接早退避免后续 try 块不可达
  // #ifdef APP-PLUS
  try {
    _schemeCleanup = registerMoyuyoScheme((ret) => {
      if (!ret || !ret.orderNo) return
      // 1) 存入支付结果 storage，pay.vue 的 readPayResultFromStorage 会消费
      try {
        uni.setStorageSync(
          'moyuyo_pay_result',
          JSON.stringify({
            type: 'pay_result',
            status: ret.status || '',
            orderNo: ret.orderNo,
            raw: ret.raw,
            timestamp: Date.now(),
          }),
        )
      } catch (e) {
        /* 写入失败可忽略 */
      }
      // 2) 如果当前在支付页/订单列表，不要硬跳，交给 pay.vue 的 onShow 处理
      const pages = getCurrentPages()
      const top = (pages && pages[pages.length - 1]) || {}
      const route = (top.route || top.$page?.path || '').replace(/^\/+/, '')
      const isPayOrDetail =
        route === 'pages/order/pay' ||
        route === 'pages/order/detail' ||
        route === 'pages/order/list'
      if (!isPayOrDetail) {
        // 支付后回到首页、商品详情等场景 → 跳去订单详情
        uni.redirectTo({
          url: `/pages/order/detail?orderNo=${encodeURIComponent(ret.orderNo)}`,
        })
      }
    })
  } catch (e) {
    /* APP 端运行时报错可忽略(H5 等平台没有 plus.runtime) */
  }
  // #endif
}

export default {
  onLaunch() {
    console.log('[MOYUYO] App Launch')

    // 主题初始化
    const themeStore = useThemeStore()
    themeStore.applyTheme()

    // 校验已保存的 Token 是否有效
    const userStore = useUserStore()
    if (userStore.token) {
      // 注册/刷新当前设备到后端,让"我的设备列表"能展示此 APP 实例
      // 不阻塞启动,失败仅打 warn
      userStore.upsertCurrentDevice()
      // 冷启动时也启动续期定时器（处理用户杀进程后用 onLaunch 重启 APP 的场景）
      userStore.startAutoRefresh()
      // 冷启动探活：复用 tryProbe，与 onShow 探活共享 30s 节流窗口
      tryProbe(userStore, 'onLaunch')
    }

    // APP 端：支付回跳 scheme 监听（冷启动时，若系统带 url 唤起 APP）
    ensureSchemeRegistered()

    // M4：标记 onLaunch 完成，onShow 内的 pinia 依赖可以安全使用
    appLaunched = true
  },
  onShow() {
    // M4：onShow 可能在 onLaunch 完成前触发（极端平台时序），早退等下次
    if (!appLaunched) {
      console.log('[MOYUYO] App onShow before onLaunch, skip')
      return
    }
    // APP 端：从后台回到前台时，重新确保 scheme 监听存在
    // （例如 iPhone 用户用 Apple Pay 后切回来，iOS 会用 scheme 打开一次 APP）
    ensureSchemeRegistered()
    // 从后台切回前台时主动探活：避免页面 onShow 才发请求导致 OS 网络栈 keep-alive
    // 失效直接 fail（30 分钟后台切回的典型场景）。先发一个轻量请求预热：
    // 1) 触发 request.js 的 fail 分支探测网络 → 必要时重试一次
    // 2) 触发 401 流程 → refresh + 自动重发原请求
    // 静默执行不弹任何 toast/modal（showError:false + 不解包）
    const userStore = useUserStore()
    // 启动续期定时器（处理后台期间定时器被 OS 回收的场景；startAutoRefresh 内部幂等）
    userStore.startAutoRefresh()
    // 后台切回探活：复用 tryProbe，30s 节流防重复请求
    tryProbe(userStore, 'onShow')
    // 全局上报今日活跃：与 home.vue 共用同一份去重实现，
    // 即便用户停留在「我的 / 社区 / 宠物」等非首页 tab，从后台切回也会刷新 last_login_time。
    // 同一用户多设备登录只会 UPDATE 同一 userId 行 → 实时在线用户 COUNT 物理去重。
    reportActiveHeartbeat()
  },
  onHide() {
    // 进入后台
  },
}
</script>

<style lang="scss">
/* 注意 SCSS 规范:所有 at-rule(@use / @forward / @import)必须出现在
 * 任何普通 CSS 规则之前,且互相之间顺序不限。*/
/* 1) 公共变量、mixin */
@use '@/styles/common.scss' as *;

/* 2) uView Plus 全局样式
 * 必须用 @import 而非 @forward:@forward 会创建模块隔离作用域,
 * 导致 uni.scss 全局注入的 $u-border-color 等变量无法穿透到
 * uview-plus/libs/css/common.scss,编译报 Undefined variable。
 * @import 共享作用域,uni.scss 变量可在整个导入链中解析。
 * (vite.config 已 silence legacy-js-api/import 弃用警告)*/
@import 'uview-plus/index.scss';

/* 3) Lucide 图标字体 + 类名(H5 / APP 通用):
 * 用 @import 把 src/styles/lucide.css 内联进 App.vue 全局样式。
 * uni-app 编译器会自动处理 url('/static/fonts/lucide.ttf'),
 * H5 端把它打到输出根目录,APP 端把它打进 _www/static/,
 * 原生 webview 从 _www/ 加载,/static/... 路径合法。
 * 这样 H5 和 APP 都能正常显示图标,无需运行时 JS 注入。*/
@import url('@/styles/lucide.css');

/* ============================================================
 * 全局 scroll-view 宽度自适应修复（H5 专用）
 *
 * 背景：uni-app 在 H5 端会把 <scroll-view> 编译为多层嵌套的 div：
 *   <scroll-view> -> <div class="uni-scroll-view">
 *     <div class="uni-scroll-view">
 *       <div class="uni-scroll-view-content">...</div>
 *     </div>
 *   </div>
 *
 * 外层 .scroll / .content 即使设了 width:100%，内层嵌套 div
 * 也可能因 display 默认值收缩（特别当 padding+box-sizing 未生效时）。
 *
 * 策略：直接给 H5 渲染出来的 .uni-scroll-view 和 .uni-scroll-view-content
 * 强制 width:100% + box-sizing:border-box，避免每个组件单独写 :deep()。
 *
 * 影响范围：仅 H5 平台（uni-app 小程序/APP 端 scroll-view 编译产物不同，不受影响）。
 * 不影响 scroll-x 横向滚动（横向滚动由子元素 flex 布局溢出实现，与父容器 width 无关）。
 * ============================================================ */
.uni-scroll-view,
.uni-scroll-view-content {
  width: 100%;
  box-sizing: border-box;
}
</style>
