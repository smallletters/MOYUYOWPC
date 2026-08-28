<template>
  <view class="pay-page">
    <view class="status-bar">
      <view class="status-icon" :class="status">⏳</view>
      <text class="status-text">{{ statusText || $t('orderLogistics.status.PENDING') }}</text>
    </view>

    <view v-if="order" class="card order-summary">
      <text class="card-title">Order #{{ order.orderNo }}</text>
      <view class="summary-row">
        <text>{{ $t('orderPay.payAmount') }}</text>
        <text class="amount">{{ currencySymbol }}{{ order.payAmount }}</text>
      </view>
      <view class="summary-row">
        <text>{{ $t('orderDetail.status') }}</text>
        <text>{{ statusLabel(order.status) }}</text>
      </view>
    </view>

    <view v-if="status === 'pending'" class="pay-methods">
      <view class="card method-card" @click="payChannel = 'STRIPE'">
        <view class="radio" :class="{ checked: payChannel === 'STRIPE' }">
          <text v-if="payChannel === 'STRIPE'"><text class="luc luc-check" /></text>
        </view>
        <view class="method-info">
          <text class="method-name">Credit / Debit Card</text>
          <text class="method-desc">Visa, Mastercard, Amex via Stripe</text>
        </view>
      </view>

      <view class="card method-card" @click="payChannel = 'PAYPAL'">
        <view class="radio" :class="{ checked: payChannel === 'PAYPAL' }">
          <text v-if="payChannel === 'PAYPAL'"><text class="luc luc-check" /></text>
        </view>
        <view class="method-info">
          <text class="method-name">PayPal</text>
          <text class="method-desc">Pay with your PayPal account</text>
        </view>
      </view>

      <view class="btn btn-primary pay-btn" @click="onPay">
        Pay with {{ payChannel === 'STRIPE' ? 'Card' : 'PayPal' }}
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

export default {
  data() {
    return {
      orderId: null,
      order: null,
      payChannel: 'STRIPE',
      status: 'pending',
      statusText: 'Select payment method',
      payUrl: '',
      pollTimer: null,
    }
  },

  onLoad(query) {
    this.orderId = query.id
    this.loadOrder()
    // H2 修复：进入支付页时尝试读取 localStorage 兜底（兼容 H5 跳转场景）
    this.readPayResultFromStorage()
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
  },

  methods: {
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
      } catch (e) {
        uni.showToast({ title: 'Failed to load order', icon: 'none' })
      }
    },

    async onPay() {
      uni.showLoading({ title: 'Processing...', mask: true })
      try {
        // H2 修复：透传 returnUrl 给后端，让 Stripe/PayPal 支付完成后跳回 moyuyo.com 上的中转页
        // 中转页（moyuyo-server/moyuyo-api/src/main/resources/static/payment/return.html）
        // 通过 window.parent.postMessage 把结果回传给 web-view
        const returnUrl = this.buildReturnUrl()
        const res = await orderApi.createPayment({
          orderNo: this.order.orderNo,
          payChannel: this.payChannel,
          returnUrl,
        })
        uni.hideLoading()

        // H1 修复：优先使用 sessionUrl（Stripe Checkout Session 标准 URL），前端 web-view 直接打开
        // 兜底仅支持旧版 PaymentIntent + clientSecret 的场景
        if (res.sessionUrl) {
          this.payUrl = res.sessionUrl
          this.startPolling()
        } else if (this.payChannel === 'STRIPE' && res.clientSecret) {
          // 兜底：用 Stripe Payment Intent 直接跳 Stripe 托管支付页
          this.payUrl = `https://checkout.stripe.com/c/pay/${res.clientSecret}`
          this.startPolling()
        } else if (res.approvalUrl) {
          // PayPal approvalUrl
          this.payUrl = res.approvalUrl
          this.startPolling()
        } else {
          this.status = 'success'
          this.statusText = 'Payment processing'
          setTimeout(() => this.goDetail(), 1500)
        }
      } catch (e) {
        uni.hideLoading()
        this.status = 'failed'
        this.statusText = e.message || 'Payment failed'
      }
    },

    /**
     * 构造 returnUrl：指向后端 static/payment/return.html 静态中转页，
     * 该页面通过 window.parent.postMessage 把支付结果回传给 web-view。
     */
    buildReturnUrl() {
      // H1 修复：使用专门的支付回跳域名（避免与 WordPress wpBase 混淆）
      // dev 环境 payReturnBase 为空时回落到 window.location.origin 走同源 Vite proxy
      const base =
        this.$config?.payReturnBase || (typeof window !== 'undefined' ? window.location.origin : '')
      return `${base}/payment/return.html`
    },

    startPolling() {
      this.status = 'processing'
      this.statusText = 'Waiting for payment...'
      this.stopPolling()
      this.pollTimer = setInterval(async () => {
        try {
          const order = await orderApi.getOrderDetail(this.orderId)
          if (order.status === 'PAID') {
            this.status = 'success'
            this.statusText = 'Payment successful!'
            this.stopPolling()
            // 支付成功:同步移除本地待付款暂存记录
            removePendingOrder(this.orderId)
            setTimeout(() => this.goDetail(), 1500)
          } else if (order.status === 'CANCELLED') {
            this.status = 'failed'
            this.statusText = 'Payment cancelled'
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
        if (uni.getStorageSync('moyuyo_pay_result_handled') === data.timestamp || raw._handled)
          return
        // 标记已消费 + 立即清空，防止下次进入再触发
        uni.setStorageSync('moyuyo_pay_result_handled', Date.now())
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
          this.handlePayResult('cancelled', '订单已取消')
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
