<template>
  <view v-if="order" class="order-detail">
    <view class="page-header">
      <view class="header-back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('orderDetail.title') }}</text>
    </view>
    <view class="status-banner" :class="`status-${order.status}`">
      <text class="status-title">{{ statusTitle }}</text>
      <text class="status-desc">{{ statusDesc }}</text>
      <text
        v-if="order.status === 'PENDING_SHIP' || order.status === 'PENDING_RECEIVE'"
        class="status-eta"
      >
        {{ $t('orderDetail.eta', { date: estimatedDelivery }) }}
      </text>
    </view>

    <!-- Logistics Timeline -->
    <view v-if="logisticsTraces.length" class="card logistics-card">
      <text class="card-title">{{ $t('orderDetail.logistics') }}</text>
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
      <text class="card-title">{{ $t('orderDetail.address') }}</text>
      <text class="address-name">{{ order.receiverName }} · {{ order.receiverPhone }}</text>
      <text class="address-detail">
        {{ order.receiverAddress }}
      </text>
      <text v-if="order.receiverZip" class="address-zip">{{ order.receiverZip }}</text>
    </view>

    <view class="card items-card">
      <text class="card-title">{{ $t('orderDetail.items') }}</text>
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
        <text>{{ $t('orderDetail.price.subtotal') }}</text>
        <text>${{ order.goodsAmount }}</text>
      </view>
      <view class="price-row">
        <text>{{ $t('orderDetail.price.shipping') }}</text>
        <text>${{ order.freight }}</text>
      </view>
      <view v-if="order.taxAmount > 0" class="price-row">
        <text>{{ $t('orderDetail.price.tax') }}</text>
        <text>${{ order.taxAmount }}</text>
      </view>
      <view v-if="order.couponDiscount > 0" class="price-row">
        <text>{{ $t('orderDetail.price.discount') }}</text>
        <text class="discount">-${{ order.couponDiscount }}</text>
      </view>
      <view v-if="order.pointsDiscount > 0" class="price-row">
        <text>{{ $t('orderDetail.price.points') }}</text>
        <text class="discount">-${{ order.pointsDiscount }}</text>
      </view>
      <view class="price-row total">
        <text>{{ $t('orderDetail.price.total') }}</text>
        <text class="total-amount">${{ order.payAmount }}</text>
      </view>
    </view>

    <view class="card info-card">
      <view class="info-row">
        <text>{{ $t('orderDetail.info.orderNumber') }}</text>
        <text>#{{ order.orderNo }}</text>
      </view>
      <view class="info-row">
        <text>{{ $t('orderDetail.info.paymentMethod') }}</text>
        <text>{{ order.payChannel || '-' }}</text>
      </view>
      <view class="info-row">
        <text>{{ $t('orderDetail.info.orderTime') }}</text>
        <text>{{ formatDate(order.createTime) }}</text>
      </view>
      <view v-if="order.paidAt" class="info-row">
        <text>{{ $t('orderDetail.info.paidAt') }}</text>
        <text>{{ formatDate(order.paidAt) }}</text>
      </view>
      <view v-if="order.trackingNumber" class="info-row">
        <text>{{ $t('orderDetail.info.tracking') }}</text>
        <text>{{ order.trackingNumber }}</text>
      </view>
    </view>

    <view class="action-bar safe-area-bottom">
      <view class="btn btn-text" @click="onCS">
        <text class="cs-icon luc-message-circle" />
        <text>{{ $t('orderDetail.contactCS') }}</text>
      </view>
      <view v-if="order.status === 'PENDING_PAY'" class="btn btn-outline" @click="onCancel">
        {{ $t('orderDetail.cancel') }}
      </view>
      <view v-if="order.status === 'PENDING_PAY'" class="btn btn-primary" @click="onPay">
        {{ $t('orderDetail.pay') }}
      </view>
      <view
        v-if="order.status === 'PENDING_SHIP' || order.status === 'PENDING_RECEIVE'"
        class="btn btn-primary"
        @click="onTrack"
      >
        {{ $t('orderDetail.track') }}
      </view>
      <view v-if="order.status === 'PENDING_RECEIVE'" class="btn btn-outline" @click="onConfirm">
        {{ $t('orderDetail.confirmReceive') }}
      </view>
      <view v-if="order.status === 'COMPLETED'" class="btn btn-primary" @click="onReview">
        {{ $t('orderDetail.review') }}
      </view>
      <view
        v-if="order.status === 'PAID' || order.status === 'RECEIVED'"
        class="btn btn-outline"
        @click="onRefund"
      >
        {{ $t('orderDetail.refund') }}
      </view>
    </view>
  </view>
  <view v-else class="loading">{{ $t('common.loading') }}</view>
</template>

<script>
import { orderApi } from '@/api'
import { i18n, t } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.orderDetail',

  data() {
    return {
      orderId: null,
      order: null,
      logisticsTraces: [],
    }
  },

  computed: {
    statusTitle() {
      const code = this.order?.status
      if (!code) return ''
      // 优先用全局订单状态字典
      const localized = t(`orderStatus.${code}`)
      if (localized && localized !== `orderStatus.${code}`) return localized
      return code
    },
    statusDesc() {
      const code = this.order?.status
      if (!code) return ''
      const localized = t(`orderDetail.statusDesc.${code}`)
      if (localized && localized !== `orderDetail.statusDesc.${code}`) return localized
      return ''
    },
    estimatedDelivery() {
      if (!this.order?.createTime) return ''
      const d = new Date(this.order.createTime)
      d.setDate(d.getDate() + 5)
      // 根据当前 locale 选择日期格式
      const isZh = i18n.locale === 'zh-CN'
      return d.toLocaleDateString(isZh ? 'zh-CN' : 'en-US', {
        month: isZh ? 'long' : 'short',
        day: 'numeric',
      })
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
        uni.showToast({ title: t('orderDetail.loadFailed'), icon: 'none' })
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
      const d = new Date(dateStr)
      // 跟随当前 locale
      return d.toLocaleString(i18n.locale)
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
        title: t('orderDetail.cancelConfirmTitle'),
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.cancelOrder(this.orderId, '')
              this.loadOrder()
            } catch (e) {
              uni.showToast({ title: t('orderDetail.cancelFailed'), icon: 'none' })
            }
          }
        },
      })
    },

    onConfirm() {
      uni.showModal({
        title: t('orderDetail.confirmReceivedTitle'),
        success: async (res) => {
          if (res.confirm) {
            try {
              await orderApi.confirmReceived(this.orderId)
              this.loadOrder()
            } catch (e) {
              uni.showToast({ title: t('orderDetail.confirmFailed'), icon: 'none' })
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
      uni.showToast({ title: t('orderDetail.csComingSoon'), icon: 'none' })
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
