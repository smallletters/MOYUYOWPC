<template>
  <view class="pay-page">
    <view class="status-bar">
      <view class="status-icon" :class="status">⏳</view>
      <text class="status-text">{{ statusText }}</text>
    </view>

    <view v-if="order" class="card order-summary">
      <text class="card-title">Order #{{ order.orderNo }}</text>
      <view class="summary-row">
        <text>Amount</text>
        <text class="amount">${{ order.payAmount }}</text>
      </view>
      <view class="summary-row">
        <text>Status</text>
        <text>{{ statusLabel(order.status) }}</text>
      </view>
    </view>

    <view v-if="status === 'pending'" class="pay-methods">
      <view class="card method-card" @click="payChannel = 'STRIPE'">
        <view class="radio" :class="{ checked: payChannel === 'STRIPE' }">
          <text v-if="payChannel === 'STRIPE'"><text class="luc luc-check"></text></text>
        </view>
        <view class="method-info">
          <text class="method-name">Credit / Debit Card</text>
          <text class="method-desc">Visa, Mastercard, Amex via Stripe</text>
        </view>
      </view>

      <view class="card method-card" @click="payChannel = 'PAYPAL'">
        <view class="radio" :class="{ checked: payChannel === 'PAYPAL' }">
          <text v-if="payChannel === 'PAYPAL'"><text class="luc luc-check"></text></text>
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
        const res = await orderApi.createPayment({
          orderNo: this.order.orderNo,
          payChannel: this.payChannel,
        })
        uni.hideLoading()

        if (this.payChannel === 'STRIPE' && res.approvalUrl) {
          this.payUrl = res.approvalUrl
          this.startPolling()
        } else if (this.payChannel === 'STRIPE' && res.clientSecret) {
          this.payUrl = `https://checkout.stripe.com/pay/${res.clientSecret}`
          this.startPolling()
        } else if (this.payChannel === 'PAYPAL' && res.approvalUrl) {
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
        if (data.status === 'success') {
          this.status = 'success'
          this.statusText = 'Payment successful!'
        } else {
          this.status = 'failed'
          this.statusText = data.message || 'Payment failed'
        }
        this.stopPolling()
      }
    },

    statusLabel(status) {
      const map = { PENDING_PAY: 'To Pay', PAID: 'Paid', CANCELLED: 'Cancelled' }
      return map[status] || status
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
