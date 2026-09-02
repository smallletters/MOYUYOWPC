<template>
  <view class="pay-page">
    <view class="status-bar">
      <view class="status-icon" :class="status">⏳</view>
      <text class="status-text">{{ statusText || $t('orderLogistics.status.PENDING') }}</text>
    </view>

    <view v-if="order" class="card order-summary">
      <text class="card-title">{{ $t('orderPay.orderNoFmt', { no: order.orderNo }) }}</text>
      <view class="summary-row">
        <text>{{ $t('orderPay.payAmount') }}</text>
        <!-- 金额必须做 Number() + toFixed(2)，避免把字符串或非数值对象渲染成 function () { [native code] }74 这种异常 -->
        <!-- currencySymbol 原本在 methods 中，模板漏写 () 会把函数对象渲染成 "function () { [native code] }" -->
        <!-- 改成内联字符串 + 函数调用，保证金额正确显示 -->
        <text class="amount">{{ currencySymbol() }}{{ formatAmount(order.payAmount) }}</text>
      </view>
      <view class="summary-row">
        <text>{{ $t('orderDetail.status') }}</text>
        <text>{{ statusLabel(order.status) }}</text>
      </view>
    </view>

    <view v-if="status === 'pending'" class="pay-methods">
      <!-- 与 checkout.vue 的 4 种支付方式保持一致，id 与 checkout 对齐（googlepay/applepay/paypal/card） -->
      <view
        v-for="m in currentPaymentMethods"
        :key="m.id"
        class="card method-card"
        :class="{ disabled: !!payUrl }"
        @click="selectPayment(m.id)"
      >
        <view class="radio" :class="{ checked: selectedMethodId === m.id }">
          <text v-if="selectedMethodId === m.id"><text class="luc luc-check" /></text>
        </view>
        <view class="method-info">
          <text class="method-name">{{ m.name }}</text>
          <text class="method-desc">{{ m.desc }}</text>
        </view>
      </view>

      <view class="btn btn-primary pay-btn" :disabled="!!payUrl" @click="onPay">
        <!-- 按钮文案随所选支付方式动态变化，避免写死 Pay with Card / PayPal -->
        {{ $t('orderPay.payBtnFmt', { label: currentPaymentLabel }) }}
      </view>
    </view>

    <view v-if="payUrl" class="webview-wrap">
      <web-view :src="payUrl" @message="onWebviewMessage" />
    </view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { removePendingOrder } from '@/utils/storage'
import { i18n } from '@/i18n'
// APP 端（iOS/Android 原生打包）走 Stripe Checkout WebView + payAppBridge 拦截 scheme：
//   - 子 WebView 加载 Stripe sessionUrl/approvalUrl
//   - overrideUrlLoading 拦截 Apple Pay / Google Pay / PayPal 等 scheme 跳转对应 APP
//   - 命中 moyuyo://pay/return 时关闭 WebView + 处理结果
// 美国市场目前支持：Google Pay / Apple Pay / PayPal / Credit Card。
// 暂不集成第三方 Stripe 原生插件（无开源免费版），靠 Stripe Checkout + scheme 拦截兜底。
import {
  createPaymentWebView,
  registerMoyuyoScheme,
} from '@/utils/payAppBridge'

export default {
  pageTitleKey: 'pageTitle.orderPay',

  data() {
    return {
      orderId: null,
      order: null,
      // 与 checkout.vue 完全一致的支付方式 ID 列表：文案走 i18n,通过 currentPaymentMethods 渲染
      paymentMethods: [
        { id: 'googlepay' },
        { id: 'applepay' },
        { id: 'paypal' },
        { id: 'card' },
      ],
      // 前端 UI 层的选中 ID（googlepay/applepay/paypal/card）
      selectedMethodId: 'googlepay',
      clientType: 'H5',
      status: 'pending',
      statusText: '',
      payUrl: '',
      pollTimer: null,
      // checkout 页传 channel/method 时为 true，订单加载完后自动发起支付
      autoPayReady: false,
      // APP 端专用：payAppBridge 创建的子 WebView 实例
      appBridge: null,
      // 清理 scheme 全局监听用
      schemeOff: null,
    }
  },

  computed: {
    /** 把 UI 层的 methodId（googlepay 等）映射为后端需要的 { channel, method } */
    paymentMapping() {
      const map = {
        googlepay: { channel: 'STRIPE', method: 'GOOGLE_PAY' },
        applepay: { channel: 'STRIPE', method: 'APPLE_PAY' },
        paypal: { channel: 'PAYPAL', method: 'PAYPAL' },
        card: { channel: 'STRIPE', method: 'CARD' },
      }
      return map[this.selectedMethodId] || map.googlepay
    },
    /** 与旧代码兼容：payChannel 取自 mapping，避免改动 onPay 等函数 */
    payChannel() {
      return this.paymentMapping.channel
    },
    /** 与旧代码兼容：payMethod 取自 mapping */
    payMethod() {
      return this.paymentMapping.method
    },
    /** 按钮文案：Google Pay / PayPal / Venmo / Apple Pay / Alipay */
    currentPaymentLabel() {
      const m = this.paymentMethods.find((p) => p.id === this.selectedMethodId)
      // 默认落在 googlepay.name,字典里一定存在,无须额外兜底
      if (!m) return i18n.t('orderPay.methods.googlepay.name')
      return i18n.t(`orderPay.methods.${m.id}.name`)
    },
    /** 用于模板渲染的支付方式列表（注入 i18n 文案） */
    currentPaymentMethods() {
      return this.paymentMethods.map((p) => ({
        id: p.id,
        name: i18n.t(`orderPay.methods.${p.id}.name`),
        desc: i18n.t(`orderPay.methods.${p.id}.desc`),
      }))
    },
    // 兼容原模板里的 statusLabel 等调用（确保 computed 与原结构一致）
    localeVersion() {
      return i18n && i18n.locale ? i18n.locale : 'en'
    },
  },

  onLoad(query) {
    this.orderId = query.id
    // checkout 页已选支付方式（带 channel/method 参数）→ 反查 UI 选中 ID 并自动支付
    if (query.method) {
      this.selectedMethodId = this.resolveMethodIdFromBackend(query.method.toUpperCase())
    } else if (query.channel) {
      // 只有 channel 没 method 时做兜底：STRIPE→googlepay，PAYPAL→paypal
      const c = query.channel.toUpperCase()
      this.selectedMethodId = c === 'PAYPAL' ? 'paypal' : 'googlepay'
    }
    // 客户端类型：APP 端走自定义 scheme 回跳，H5 走中转页 postMessage
    if (query.clientType) {
      this.clientType = query.clientType.toUpperCase()
    } else {
      // query 没带时，按当前平台兜底（仅 APP 端是 APP，其他端保持 H5）
      // #ifdef APP-PLUS
      this.clientType = 'APP'
      // #endif
      // #ifdef MP-WEIXIN
      this.clientType = 'MP'
      // #endif
    }
    this.loadOrder()
    this.readPayResultFromStorage()
    // checkout 页带了 channel → 订单加载完立即自动发起支付（原 query.channel 判断保留）
    if (query.channel) {
      this.autoPayReady = true
    }
    if (this.clientType === 'APP') {
      try {
        this.schemeOff = registerMoyuyoScheme((ret) => {
          this.handlePayResult(ret.status, ret.status === 'success' ? '' : i18n.t('orderPay.payCancelled'))
        })
      } catch (e) {
        /* APP 端运行时报错可忽略 */
      }
    }
  },

  onShow() {
    // H4 修复：从 web-view 切回 APP 时主动拉一次订单详情，避免轮询停摆后丢失回调
    // 同时检查 localStorage 兜底（H5 浏览器支付后通过 storage 回传）
    this.readPayResultFromStorage()
    if (this.orderId) {
      this.pollOrderOnce()
    }
  },

  onUnload() {
    this.stopPolling()
    // 关闭子 WebView，避免"返回到支付页还显示 Stripe 页面"
    if (this.appBridge && typeof this.appBridge.close === 'function') {
      try {
        this.appBridge.close()
      } catch (e) {
        /* 忽略关闭失败 */
      }
      this.appBridge = null
    }
    if (typeof this.schemeOff === 'function') {
      try {
        this.schemeOff()
      } catch (e) {
        /* 忽略清理失败 */
      }
      this.schemeOff = null
    }
  },

  methods: {
    /** 安全格式化金额：Number() + toFixed(2)，任何非法值都回落到 0.00 */
    formatAmount(v) {
      const n = Number(v)
      return Number.isFinite(n) ? n.toFixed(2) : '0.00'
    },
    /** 把后端 payMethod（APPLE_PAY/GOOGLE_PAY/PAYPAL/CARD 等大写）
     *  反查回前端 UI 层的 methodId（applepay/googlepay/paypal/card）。
     *  找不到时默认 googlepay，避免空白选中。 */
    resolveMethodIdFromBackend(backendMethod) {
      const m = (backendMethod || '').toUpperCase().trim()
      const map = {
        APPLE_PAY: 'applepay',
        APPLEPAY: 'applepay',
        GOOGLE_PAY: 'googlepay',
        GOOGLEPAY: 'googlepay',
        PAYPAL: 'paypal',
        CARD: 'card',
        // 兼容旧版数据
        VENMO: 'paypal',
        ALIPAY: 'card',
        CASH_APP: 'card',
        CASHAPP: 'card',
        AFFIRM: 'card',
        AFTERPAY: 'card',
        LINK: 'card',
      }
      return map[m] || 'googlepay'
    },
    /** 点击支付方式卡片：切换选中（已经打开 WebView 时禁止切） */
    selectPayment(id) {
      if (this.payUrl) return
      if (!this.paymentMethods.some((p) => p.id === id)) return
      this.selectedMethodId = id
    },
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
        // checkout 页带 channel 参数 → 订单加载完自动发起支付
        if (this.autoPayReady && this.order?.status === 'PENDING_PAY') {
          this.autoPayReady = false
          setTimeout(() => this.onPay(), 300)
        }
      } catch (e) {
        uni.showToast({ title: i18n.t('orderPay.loadFailed'), icon: 'none' })
      }
    },

    async onPay() {
      uni.showLoading({ title: i18n.t('orderPay.processing'), mask: true })
      try {
        // APP 端传 clientType=APP + schemeBase=moyuyo://pay/return，
        // 后端会把 Stripe/PayPal 的 success/cancel 回跳地址写成 moyuyo://pay/return?status=...
        // 再由 payAppBridge / App.vue scheme 监听触发回到 Moyuyo APP，显示订单详情
        // H5 仍然走 return.html 中转页（postMessage + localStorage 回传）
        const returnUrl = this.buildReturnUrl()
        const client = this.buildClientPayload()
        const res = await orderApi.createPayment({
          orderNo: this.order.orderNo,
          payChannel: this.payChannel,
          payMethod: this.payMethod,
          returnUrl,
          clientType: client.clientType,
          schemeBase: client.schemeBase,
        })
        uni.hideLoading()

        // 美国市场方案（无 Stripe 原生插件）：
        //   - 直接走 Stripe Checkout WebView（sessionUrl）
        //   - 子 WebView 的 overrideUrlLoading 拦截 Apple Pay / Google Pay / PayPal scheme
        //     真正跳到对应 APP 唤起 Face ID / G Pay 界面
        //   - 付完通过 moyuyo://pay/return 回跳到 APP
        let finalUrl = ''
        if (res.sessionUrl) {
          finalUrl = res.sessionUrl
        } else if (res.approvalUrl) {
          finalUrl = res.approvalUrl
        } else if (this.payChannel === 'STRIPE' && res.clientSecret) {
          // 兜底：兼容老版本 PaymentIntent 流程
          finalUrl = `https://checkout.stripe.com/c/pay/${res.clientSecret}`
        } else {
          this.status = 'success'
          this.statusText = i18n.t('orderPay.statusText.processingInline')
          setTimeout(() => this.goDetail(), 1500)
          return
        }
        this.payUrl = finalUrl
        this.openPayPage(finalUrl)
        this.startPolling()
      } catch (e) {
        uni.hideLoading()
        this.status = 'failed'
        this.statusText = e.message || i18n.t('orderPay.payFailed')
      }
    },

    /**
     * 打开支付页：
     * - APP 端：使用 payAppBridge 创建子 WebView，自定义 scheme 拦截保证能跳对方 APP，
     *   并且命中 moyuyo://pay/return 时直接关 WebView + 处理结果（避免回跳丢单）。
     * - H5 / MP 端：继续用 <web-view> 组件（this.payUrl 响应式渲染）。
     */
    openPayPage(url) {
      if (this.clientType !== 'APP') return
      // APP 端：复用单例子 WebView，避免每次打开都 append 新层
      const bridge =
        this.appBridge ||
        createPaymentWebView({
          page: this,
          onReturn: (ret) => {
            this.handlePayResult(ret.status || '', ret.status === 'success' ? '' : i18n.t('orderPay.payCancelled'))
          },
          onMessage: (e) => {
            const data = e.detail?.data?.[0]
            if (data?.type === 'pay_result') this.handlePayResult(data.status, data.message)
          },
        })
      this.appBridge = bridge
      bridge.loadURL(url).then((wv) => {
        if (!wv) {
          // plus/webview 不可用（例如 H5 运行期），继续走 uni <web-view> 兜底
          this.payUrl = url
        }
      })
    },

    /**
     * 构造 returnUrl：指向后端 static/payment/return.html 静态中转页，
     * 该页面通过 window.parent.postMessage 把支付结果回传给 web-view。
     */
    buildReturnUrl() {
      // 构造请求体额外参数：
      // - clientType：H5 / APP / MP。APP 端后端 successUrl/cancelUrl 走自定义 scheme，
      //   保证 Stripe Checkout / PayPal 跳转到外部 APP 付完款后能回到你的 Moyuyo APP
      // - schemeBase：自定义 scheme 地址（iOS/Android 打包都要注册）
      return (
        this.$config?.payReturnBase || (typeof window !== 'undefined' ? window.location.origin : '')
      )
    },

    /**
     * 组装 CreatePaymentRequest 的 clientType + schemeBase。
     * - H5：returnUrl 为中转页域名
     * - APP：clientType=APP，schemeBase=moyuyo://pay/return
     * - 小程序：clientType=MP，走中转页
     */
    buildClientPayload() {
      // 优先使用 onLoad 时从 query / 条件编译得到的 clientType，避免被本地条件编译覆盖
      // APP 端下发 schemeBase=moyuyo://pay/return → 后端把 success/cancel URL 写成自定义 scheme，
      // 第三方 APP 付完款后用 scheme 唤回 Moyuyo APP
      let clientType = this.clientType || 'H5'
      // 兜底：如果 query 没传 clientType 且当前是 APP 端，按 APP 处理
      // #ifdef APP-PLUS
      if (!this.clientType) clientType = 'APP'
      // #endif
      return {
        clientType,
        schemeBase: clientType === 'APP' ? 'moyuyo://pay/return' : '',
      }
    },

    startPolling() {
      this.status = 'processing'
      this.statusText = i18n.t('orderPay.statusText.waiting')
      this.stopPolling()
      this.pollTimer = setInterval(async () => {
        try {
          const order = await orderApi.getOrderDetail(this.orderId)
          if (order.status === 'PAID') {
            this.status = 'success'
            this.statusText = i18n.t('orderPay.statusText.success')
            this.stopPolling()
            // 支付成功:同步移除本地待付款暂存记录
            removePendingOrder(this.orderId)
            setTimeout(() => this.goDetail(), 1500)
          } else if (order.status === 'CANCELLED') {
            this.status = 'failed'
            this.statusText = i18n.t('orderPay.statusText.cancelled')
            this.stopPolling()
          }
        } catch (e) {
          console.error('[pay] poll error', e)
        }
      }, 3000)
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    onWebviewMessage(e) {
      const data = e.detail?.data?.[0]
      if (data?.type === 'pay_result') {
        this.handlePayResult(data.status, data.message)
      }
    },

    /**
     * H4 修复：从 localStorage 读取中转页回传结果（兜底，兼容 web-view 不传 message 的场景）
     * 美国市场方案：moyuyoshop.com/payment/return.html 三通道 (postMessage / localStorage / scheme) 都到位后，
     * 此方法主要应对 postMessage 失败（如 iOS 15 Safari 拦截跨域）的情况。
     */
    readPayResultFromStorage() {
      try {
        const raw = uni.getStorageSync('moyuyo_pay_result')
        if (!raw || typeof raw !== 'string') return
        const data = JSON.parse(raw)
        if (data?.type !== 'pay_result') return
        // 仅消费与当前订单匹配的结果，避免历史脏数据误判
        if (data.orderNo && this.order?.orderNo && data.orderNo !== this.order.orderNo) return
        // 已处理过则跳过（避免重复跳转）
        const handledTs = uni.getStorageSync('moyuyo_pay_result_handled')
        if (handledTs && Number(handledTs) === Number(data.timestamp)) return
        // 标记已消费 + 立即清空 payload 与时间戳，防止下次进入再触发
        uni.setStorageSync('moyuyo_pay_result_handled', String(data.timestamp))
        uni.removeStorageSync('moyuyo_pay_result')
        uni.removeStorageSync('moyuyo_pay_result_timestamp')
        this.handlePayResult(data.status, data.message)
      } catch (e) {
        // ignore
      }
    },

    /**
     * H4 修复：拉一次订单状态判断是否已支付成功（onShow 用）
     */
    async pollOrderOnce() {
      try {
        const order = await orderApi.getOrderDetail(this.orderId)
        if (order.status === 'PAID') {
          this.handlePayResult('success', '')
        } else if (order.status === 'CANCELLED') {
          this.handlePayResult('cancelled', i18n.t('orderPay.cancelReason'))
        }
      } catch (e) {
        // ignore
      }
    },

    /**
     * 统一处理支付结果：更新状态文案、关闭 web-view、跳订单详情。
     */
    handlePayResult(status, message) {
      void this.localeVersion
      this.stopPolling()
      if (status === 'success') {
        this.status = 'success'
        this.statusText = i18n.t('orderPay.paySuccess') + '!'
        // 支付成功:同步移除本地待付款暂存记录
        removePendingOrder(this.orderId)
        setTimeout(() => this.goDetail(), 800)
      } else if (status === 'cancelled' || status === 'cancel') {
        this.status = 'failed'
        this.statusText = i18n.t('orderPay.payCancelled')
      } else {
        this.status = 'failed'
        this.statusText = message || i18n.t('orderPay.payFailed')
      }
      // 关闭 web-view
      this.payUrl = ''
    },

    statusLabel(status) {
      void this.localeVersion
      // 复用 orderStatus 字典,状态码对齐后端
      const key = `orderStatus.${status}`
      const v = i18n.t(key)
      return v === key ? status : v
    },

    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },

    goDetail() {
      uni.redirectTo({ url: `/pages/order/detail?id=${this.orderId}` })
    },
  },
}
</script>

<style lang="scss" scoped>
.pay-page {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 32rpx 24rpx;
  background: var(--color-surface);
}

.status-icon {
  font-size: 48rpx;
}

.status-text {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.order-summary {
  margin: 16rpx;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.amount {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.pay-methods {
  padding: 0 16rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.method-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
}

.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  color: #fff;
  flex-shrink: 0;
}

.radio.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.method-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.method-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.method-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.pay-btn {
  padding: 28rpx 0;
  font-size: var(--font-size-md);
}

.webview-wrap {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
}
</style>
