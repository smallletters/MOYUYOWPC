<template>
  <view v-if="order" class="order-detail">
    <view class="page-header">
      <view class="header-back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="header-title">订单详情</text>
    </view>
    <view class="status-banner" :class="`status-${order.status}`">
      <text class="status-title">{{ statusTitle }}</text>
      <text class="status-desc">{{ statusDesc }}</text>
      <text
        v-if="order.status === 'PENDING_SHIP' || order.status === 'PENDING_RECEIVE'"
        class="status-eta"
      >
        Estimated Delivery: {{ estimatedDelivery }}
      </text>
    </view>

    <!-- Logistics Timeline -->
    <view v-if="logisticsTraces.length" class="card logistics-card">
      <text class="card-title">Logistics</text>
      <view class="timeline">
        <view
          v-for="(t, i) in logisticsTraces"
          :key="i"
          class="timeline-item"
          :class="{ active: i === 0, done: i > 0 }"
        >
          <view class="timeline-dot" />
          <view class="timeline-body">
            <text class="timeline-desc">{{ t.desc }}</text>
            <text class="timeline-time">{{ t.time }}</text>
            <text v-if="t.location" class="timeline-location">{{ t.location }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="card address-card">
      <text class="card-title">Shipping Address</text>
      <text class="address-name">{{ order.receiverName }} · {{ order.receiverPhone }}</text>
      <text class="address-detail">
        {{ order.receiverAddress }}
      </text>
      <text v-if="order.receiverZip" class="address-zip">{{ order.receiverZip }}</text>
    </view>

    <view class="card items-card">
      <text class="card-title">Items</text>
      <view v-for="item in order.items" :key="item.id" class="item-row">
        <image :src="item.mainImage || ''" class="item-image" />
        <view class="item-info">
          <text class="item-name">{{ item.productName }}</text>
          <text v-if="item.skuSpec" class="item-spec">{{ item.skuSpec }}</text>
          <text class="item-qty">x {{ item.quantity }}</text>
        </view>
        <text class="item-price">${{ item.subtotal }}</text>
      </view>
    </view>

    <view class="card price-card">
      <view class="price-row">
        <text>Subtotal</text>
        <text>${{ order.goodsAmount }}</text>
      </view>
      <view class="price-row">
        <text>Shipping</text>
        <text>${{ order.freight }}</text>
      </view>
      <view v-if="order.taxAmount > 0" class="price-row">
        <text>Tax</text>
        <text>${{ order.taxAmount }}</text>
      </view>
      <view v-if="order.couponDiscount > 0" class="price-row">
        <text>Discount</text>
        <text class="discount">-${{ order.couponDiscount }}</text>
      </view>
      <view v-if="order.pointsDiscount > 0" class="price-row">
        <text>Points</text>
        <text class="discount">-${{ order.pointsDiscount }}</text>
      </view>
      <view class="price-row total">
        <text>Total</text>
        <text class="total-amount">${{ order.payAmount }}</text>
      </view>
    </view>

    <view class="card info-card">
      <view class="info-row">
        <text>Order Number</text>
        <text>#{{ order.orderNo }}</text>
      </view>
      <view class="info-row">
        <text>Payment Method</text>
        <text>{{ order.payChannel || '-' }}</text>
      </view>
      <view class="info-row">
        <text>Order Time</text>
        <text>{{ formatDate(order.createTime) }}</text>
      </view>
      <view v-if="order.paidAt" class="info-row">
        <text>Paid At</text>
        <text>{{ formatDate(order.paidAt) }}</text>
      </view>
      <view v-if="order.trackingNumber" class="info-row">
        <text>Tracking</text>
        <text>{{ order.trackingNumber }}</text>
      </view>
    </view>

    <view class="action-bar safe-area-bottom">
      <view class="btn btn-text" @click="onCS">
        <text class="cs-icon"><text class="luc luc-message-circle" /></text>
        <text>{{ $t('orderDetail.contactCS') }}</text>
      </view>
      <view v-if="order.status === 'PENDING_PAY'" class="btn btn-outline" @click="onCancel">
        取消订单
      </view>
      <view v-if="order.status === 'PENDING_PAY'" class="btn btn-primary" @click="onPay">
        去支付
      </view>
      <view
        v-if="order.status === 'PENDING_SHIP' || order.status === 'PENDING_RECEIVE'"
        class="btn btn-primary"
        @click="onTrack"
      >
        查看物流
      </view>
      <view v-if="order.status === 'PENDING_RECEIVE'" class="btn btn-outline" @click="onConfirm">
        确认收货
      </view>
      <view v-if="order.status === 'COMPLETED'" class="btn btn-primary" @click="onReview">
        立即评价
      </view>
      <view
        v-if="order.status === 'PAID' || order.status === 'RECEIVED'"
        class="btn btn-outline"
        @click="onRefund"
      >
        申请退款
      </view>
    </view>
  </view>
  <view v-else class="loading">Loading...</view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      orderId: null,
      order: null,
      logisticsTraces: [],
    }
  },

  computed: {
    statusTitle() {
      const map = {
        PENDING_PAY: 'Awaiting Payment',
        PENDING_SHIP: 'To Ship',
        PENDING_RECEIVE: 'To Receive',
        COMPLETED: 'Completed',
        CANCELLED: 'Cancelled',
        REFUNDING: 'Refunding',
        REFUNDED: 'Refunded',
      }
      return map[this.order?.status] || this.order?.status
    },
    statusDesc() {
      const map = {
        PENDING_PAY: 'Please complete payment before the order expires',
        PENDING_SHIP: 'Seller is preparing your order',
        PENDING_RECEIVE: 'Your order is on the way',
        COMPLETED: 'Thank you for your purchase',
        CANCELLED: 'This order has been cancelled',
      }
      return map[this.order?.status] || ''
    },
    estimatedDelivery() {
      if (!this.order?.createTime) return ''
      const d = new Date(this.order.createTime)
      d.setDate(d.getDate() + 5)
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    },
  },

  onLoad(query) {
    this.orderId = query.id
    this.loadOrder()
    this.loadLogistics()
  },

  methods: {
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
      } catch (e) {
        uni.showToast({ title: 'Load failed', icon: 'none' })
      }
    },

    async loadLogistics() {
      try {
        const logistics = await orderApi.getLogistics(this.orderId)
        if (logistics?.traces?.length) {
          this.logisticsTraces = logistics.traces
        }
      } catch (e) {
        // silently ignore
      }
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleString()
    },

    goBack() {
      uni.navigateBack()
    },

    onPay() {
      uni.navigateTo({
        url: `/pages/order/pay?orderId=${this.orderId}&amount=${this.order.payAmount}`,
      })
    },

    onCancel() {
      uni.showModal({
        title: 'Cancel order?',
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.cancelOrder(this.orderId, '')
              this.loadOrder()
            } catch (e) {
              uni.showToast({ title: 'Cancel failed', icon: 'none' })
            }
          }
        },
      })
    },

    onConfirm() {
      uni.showModal({
        title: 'Confirm received?',
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.confirmReceived(this.orderId)
              this.loadOrder()
            } catch (e) {
              uni.showToast({ title: 'Failed', icon: 'none' })
            }
          }
        },
      })
    },

    onTrack() {
      uni.navigateTo({ url: `/pages/order/logistics?id=${this.orderId}` })
    },

    onReview() {
      uni.navigateTo({ url: `/pages/order/review?orderId=${this.orderId}` })
    },

    onRefund() {
      uni.navigateTo({ url: `/pages/order/refund?orderId=${this.orderId}` })
    },

    onCS() {
      uni.showToast({ title: 'Customer service coming soon', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.order-detail {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-right: 60rpx;
}

.status-banner {
  padding: 40rpx 24rpx;
  text-align: center;
}

.status-title {
  font-size: 36rpx;
  font-weight: var(--font-weight-bold);
  display: block;
  color: var(--color-text);
}
.status-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  display: block;
  margin-top: 8rpx;
}
.status-eta {
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  display: block;
  margin-top: 8rpx;
}

.logistics-card {
  margin: 16rpx;
}

.timeline {
  padding: 8rpx 0;
}

.timeline-item {
  display: flex;
  gap: 16rpx;
  padding-bottom: 32rpx;
  position: relative;
}
.timeline-item::before {
  content: '';
  position: absolute;
  left: 14rpx;
  top: 32rpx;
  bottom: 0;
  width: 2rpx;
  background: var(--color-divider);
}
.timeline-item:last-child::before {
  display: none;
}

.timeline-dot {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: var(--color-divider);
  flex-shrink: 0;
  margin-top: 4rpx;
}
.timeline-item.active .timeline-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 6rpx rgba(219, 201, 138, 0.2);
}
.timeline-item.done .timeline-dot {
  background: var(--color-primary);
}

.timeline-body {
  flex: 1;
}
.timeline-desc {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  display: block;
}
.timeline-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  display: block;
  margin-top: 4rpx;
}
.timeline-location {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  display: block;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin: 16rpx;
}
.card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  align-items: center;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}
.action-bar .btn {
  flex-shrink: 0;
}

.cs-icon {
  font-size: 32rpx;
}
.btn-text {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
</style>
