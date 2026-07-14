<template>
  <view class="pay-page">
    <!-- 状态展示 -->
    <view class="status-bar">
      <view v-if="status === 'loading'" class="status-icon loading">⏳</view>
      <view v-else-if="status === 'success'" class="status-icon success">✓</view>
      <view v-else-if="status === 'failed'" class="status-icon failed">×</view>
      <view v-else class="status-icon loading">⏳</view>
      <text class="status-text">{{ statusText }}</text>
    </view>

    <!-- WebView 加载 WP 支付页 -->
    <web-view
      v-if="payUrl"
      :src="payUrl"
      @message="onMessage"
      @load="onLoad"
      @error="onError"
    ></web-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'
import { useCartStore } from '@/store'

export default {
  data() {
    return {
      orderId: null,
      payUrl: '',
      status: 'loading', // loading | success | failed
      statusText: '加载支付页面...',
      pollTimer: null
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    }
  },

  onLoad(query) {
    this.orderId = query.orderId
    // 实际 URL 来自 WP：/checkout/order-pay/{id}/
    // 这里使用传入的 url 或构造
    this.payUrl = query.url
      ? decodeURIComponent(query.url)
      : `https://your-wp-site.com/checkout/order-pay/${this.orderId}/`
  },

  onShow() {
    // 进入页面即开始轮询订单状态（用户从 WebView 切回 APP 时）
    this.startPolling()
  },

  onUnload() {
    this.stopPolling()
  },

  methods: {
    /**
     * 启动轮询订单状态
     */
    startPolling() {
      this.stopPolling()
      this.pollTimer = setInterval(async () => {
        try {
          const order = await orderApi.getOrderDetail(this.orderId)
          if (order.status === 'processing' || order.status === 'completed') {
            this.status = 'success'
            this.statusText = '支付成功！'
            this.stopPolling()
            this.cartStore.clear()
            // 跳转到订单详情
            setTimeout(() => {
              uni.redirectTo({ url: `/pages/order/detail?id=${this.orderId}` })
            }, 1500)
          } else if (order.status === 'cancelled' || order.status === 'refunded' || order.status === 'failed') {
            this.status = 'failed'
            this.statusText = '订单已取消'
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

    /**
     * WebView 内部页面通过 postMessage 通知 APP 支付结果
     * WP 端可注入：window.parent.postMessage({type: 'pay_result', status: 'success'}, '*')
     */
    onMessage(e) {
      const data = e.detail?.data?.[0]
      if (data?.type === 'pay_result') {
        if (data.status === 'success') {
          this.status = 'success'
          this.statusText = '支付成功！'
        } else {
          this.status = 'failed'
          this.statusText = '支付失败：' + (data.message || '')
        }
        this.stopPolling()
      }
    },

    onLoad() {
      console.log('[pay] webview loaded')
    },

    onError(e) {
      this.status = 'failed'
      this.statusText = '加载支付页面失败'
    }
  }
}
</script>

<style lang="scss" scoped>
.pay-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.status-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 32rpx 24rpx;
  background: var(--color-surface);
}

.status-icon {
  font-size: 64rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-icon.loading {
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark);
}

.status-icon.success {
  background: rgba(171, 185, 173, 0.15);
  color: var(--color-success);
}

.status-icon.failed {
  background: rgba(201, 110, 95, 0.15);
  color: var(--color-danger);
}

.status-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
}
</style>
