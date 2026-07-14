<template>
  <view class="order-detail" v-if="order">
    <view class="status-banner" :class="`status-${order.status}`">
      <text class="status-title">{{ statusTitle }}</text>
      <text class="status-desc">{{ statusDesc }}</text>
    </view>

    <view class="card address-card">
      <text class="card-title">Shipping Address</text>
      <text class="address-name">{{ order.shipping?.first_name }} {{ order.shipping?.last_name }} · {{ order.shipping?.phone }}</text>
      <text class="address-detail">{{ order.shipping?.address_1 }} {{ order.shipping?.city }} {{ order.shipping?.state }} {{ order.shipping?.postcode }} {{ order.shipping?.country }}</text>
    </view>

    <view class="card items-card">
      <text class="card-title">Items</text>
      <view v-for="item in order.line_items" :key="item.id" class="item-row">
        <image :src="item.image?.src || ''" class="item-image" />
        <view class="item-info">
          <text class="item-name">{{ item.name }}</text>
          <text class="item-qty">x {{ item.quantity }}</text>
        </view>
        <text class="item-price">${{ item.subtotal }}</text>
      </view>
    </view>

    <view class="card price-card">
      <view class="price-row"><text>Subtotal</text><text>${{ order.subtotal }}</text></view>
      <view class="price-row"><text>Shipping</text><text>${{ order.shipping_total }}</text></view>
      <view v-if="parseFloat(order.discount_total) > 0" class="price-row">
        <text>Discount</text><text class="discount">-${{ order.discount_total }}</text>
      </view>
      <view class="price-row total"><text>Total</text><text class="total-amount">${{ order.total }}</text></view>
    </view>

    <view class="card info-card">
      <view class="info-row"><text>Order Number</text><text>#{{ order.number }}</text></view>
      <view class="info-row"><text>Payment Method</text><text>{{ order.payment_method_title }}</text></view>
      <view class="info-row"><text>Order Time</text><text>{{ formatDate(order.date_created) }}</text></view>
    </view>

    <view class="action-bar safe-area-bottom">
      <view v-if="order.status === 'pending'" class="btn btn-outline" @click="onCancel">Cancel Order</view>
      <view v-if="order.status === 'pending'" class="btn btn-primary" @click="onPay">Pay Now</view>
      <view v-if="order.status === 'shipped' || order.status === 'processing'" class="btn btn-primary" @click="onTrack">Track Order</view>
      <view v-if="order.status === 'completed'" class="btn btn-primary" @click="onReview">Write Review</view>
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
      order: null
    }
  },

  computed: {
    statusTitle() {
      const map = {
        pending: 'Awaiting Payment',
        processing: 'To Ship',
        shipped: 'Shipped',
        completed: 'Completed',
        cancelled: 'Cancelled'
      }
      return map[this.order?.status] || this.order?.status
    },
    statusDesc() {
      const map = {
        pending: 'Please complete payment before the order expires',
        processing: 'Seller is preparing your order',
        shipped: 'Your order is on the way',
        completed: 'Thank you for your purchase',
        cancelled: 'This order has been cancelled'
      }
      return map[this.order?.status] || ''
    }
  },

  onLoad(query) {
    this.orderId = query.id
    this.loadOrder()
  },

  methods: {
    async loadOrder() {
      try {
        this.order = await orderApi.getOrderDetail(this.orderId)
      } catch (e) {
        uni.showToast({ title: 'Load failed', icon: 'none' })
      }
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleString()
    },

    onPay() {
      const payUrl = orderApi.getPayUrl(this.orderId)
      uni.navigateTo({ url: `/pages/webview/pay?orderId=${this.orderId}&url=${encodeURIComponent(payUrl)}` })
    },

    onCancel() {
      uni.showModal({
        title: 'Cancel order?',
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.cancelOrder(this.orderId)
              this.loadOrder()
            } catch (e) {
              uni.showToast({ title: 'Cancel failed', icon: 'none' })
            }
          }
        }
      })
    },

    onTrack() {
      uni.navigateTo({ url: `/pages/order/logistics?id=${this.orderId}` })
    },

    onReview() {
      uni.navigateTo({ url: `/pages/order/review?orderId=${this.orderId}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.order-detail {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
}

.status-banner {
  padding: 32rpx 24rpx;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.status-pending { background: linear-gradient(135deg, #E6B97A 0%, #D9AB6A 100%); }
.status-processing { background: linear-gradient(135deg, #8FA8B6 0%, #6B8896 100%); }
.status-shipped { background: linear-gradient(135deg, #ABB9AD 0%, #8A9A8C 100%); }
.status-completed { background: linear-gradient(135deg, #ABB9AD 0%, #8A9A8C 100%); }
.status-cancelled { background: linear-gradient(135deg, #9A948C 0%, #6E6962 100%); }

.status-title {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
}

.status-desc {
  font-size: var(--font-size-sm);
  opacity: 0.9;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin: 16rpx;
  padding: 24rpx;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}

.address-name {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.address-detail {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
  line-height: 1.5;
}

.item-row {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  align-items: center;
  border-bottom: 1rpx solid var(--color-divider);
}

.item-row:last-child {
  border-bottom: none;
}

.item-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.item-name {
  font-size: var(--font-size-sm);
  line-height: 1.4;
}

.item-qty {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.item-price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.price-card .price-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: var(--font-size-base);
}

.discount {
  color: var(--color-primary-dark);
}

.price-card .total {
  border-top: 1rpx solid var(--color-divider);
  padding-top: 16rpx;
  margin-top: 8rpx;
  font-weight: var(--font-weight-semibold);
}

.total-amount {
  font-size: 32rpx;
  color: var(--color-primary-dark);
}

.info-card .info-row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
  font-size: var(--font-size-sm);
}

.info-row text:first-child {
  color: var(--color-text-tertiary);
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.action-bar .btn {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
}

.loading {
  text-align: center;
  padding: 200rpx 0;
  color: var(--color-text-tertiary);
}
</style>
