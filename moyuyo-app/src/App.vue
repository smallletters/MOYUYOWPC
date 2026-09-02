<script>
import { useThemeStore } from '@/store'
import { useUserStore } from '@/store'
// 静态 import(替代原 import('@/utils/payAppBridge') 动态 import):
//   HBuilder(uni-app 3.8.12)在 App.vue 顶层出现 dynamic import 时,
//   会启用 Rollup 代码分割,但其 output.format 默认是 iife,二者冲突 → build failed.
//   静态 import 在各端都被正常打入(并被 tree-shake / 条件编译优化),
//   运行时通过 if(_schemeCleanup) 早退保证只在 APP 端真正注册监听。
import { registerMoyuyoScheme } from '@/utils/payAppBridge'

// APP 端全局 scheme 监听：
// 支付成功/取消后,Stripe Checkout / PayPal / 支付宝 APP 会用
//   moyuyo://pay/return?status=success&orderNo=xxx
// 回到你的 Moyuyo APP。全局监听：
//   1) 如果 pay 页在栈上,pay 页自己注册的监听会处理(更精确,不重复)
//   2) 如果 APP 已被系统回收 → 冷启动 → 全局监听到后直接跳到订单详情
let _schemeCleanup = null
function ensureSchemeRegistered() {
  if (_schemeCleanup) return
  // //#ifndef APP-PLUS
  return
  // //#endif
  // eslint-disable-next-line no-unreachable -- 条件编译：#ifndef 分支内 return 使该行在非 APP 端不可达
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
      // 静默拉取用户信息，失败则强制登出
      userStore.fetchProfile().catch(() => userStore.forceLogout())
    }

    // APP 端：支付回跳 scheme 监听（冷启动时，若系统带 url 唤起 APP）
    ensureSchemeRegistered()
  },
  onShow() {
    // APP 端：从后台回到前台时，重新确保 scheme 监听存在
    // （例如 iPhone 用户用 Apple Pay 后切回来，iOS 会用 scheme 打开一次 APP）
    ensureSchemeRegistered()
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

/* 2) uView Plus 全局样式(用 @forward 仅触发副作用,
 * 避免 App.vue 自己再发 DEPRECATION 警告;uview-plus/index.scss
 * 自身仍用 @import,Dart Sass 2.0 才会强制移除)*/
@forward 'uview-plus/index.scss';

/* 3) Lucide 图标字体 + 类名(H5 / APP 通用):
 * 用 @import 把 src/styles/lucide.css 内联进 App.vue 全局样式。
 * uni-app 编译器会自动处理 url('/static/fonts/lucide.ttf'),
 * H5 端把它打到输出根目录,APP 端把它打进 _www/static/,
 * 原生 webview 从 _www/ 加载,/static/... 路径合法。
 * 这样 H5 和 APP 都能正常显示图标,无需运行时 JS 注入。*/
@import url('@/styles/lucide.css');
</style>