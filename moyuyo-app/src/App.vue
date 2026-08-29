<script>
import { useThemeStore } from '@/store'
import { useUserStore } from '@/store'

// APP 端全局 scheme 监听：
// 支付成功/取消后，Stripe Checkout / PayPal / 支付宝 APP 会用
//   moyuyo://pay/return?status=success&orderNo=xxx
// 回到你的 Moyuyo APP。全局监听：
//   1) 如果 pay 页在栈上，pay 页自己注册的监听会处理（更精确，不重复）
//   2) 如果 APP 已被系统回收 → 冷启动 → 全局监听到后直接跳到订单详情
// 注：动态 import 只在 APP 编译环境执行，H5/小程序不会加载 plus.runtime
let _schemeCleanup = null
function ensureSchemeRegistered() {
  if (_schemeCleanup) return
  // //#ifndef APP-PLUS
  return
  // //#endif
  // eslint-disable-next-line no-unreachable -- 条件编译：#ifndef 分支内 return 使该行在非 APP 端不可达
  import('@/utils/payAppBridge')
    .then(({ registerMoyuyoScheme }) => {
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
    })
    .catch(() => {})
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
@use '@/styles/common.scss' as *;

/* uView Plus 全局样式 */
@import 'uview-plus/index.scss';

/* Lucide 图标字体 */
@import '@/styles/lucide.scss';
</style>
