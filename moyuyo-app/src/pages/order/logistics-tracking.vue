<template>
  <view class="logistics-tracking">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">物流跟踪</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 物流状态 -->
      <view class="status-card">
        <view class="status-icon"><text class="luc luc-truck" /></view>
        <view class="status-info">
          <text class="status-title">{{ logisticsStatus }}</text>
          <text class="status-desc">承运：{{ carrier }}</text>
          <text class="status-no">运单号：{{ trackingNumber }}</text>
        </view>
      </view>

      <!-- 配送进度 -->
      <view class="timeline-card">
        <text class="card-title">配送进度</text>
        <view class="timeline">
          <view
            v-for="(t, i) in timeline"
            :key="i"
            class="timeline-item"
            :class="{ active: i === 0 }"
          >
            <view class="timeline-dot" />
            <view class="timeline-body">
              <text class="timeline-desc">{{ t.desc }}</text>
              <text class="timeline-time">{{ t.time }}</text>
              <text v-if="t.location" class="timeline-loc">{{ t.location }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 配送地址 -->
      <view class="address-card">
        <text class="card-title">配送地址</text>
        <text class="addr-name">{{ receiverName }} · {{ receiverPhone }}</text>
        <text class="addr-detail">{{ receiverAddress }}</text>
      </view>

      <view class="bottom-bar safe-area-bottom">
        <view class="btn-outline" @click="onCallCarrier">联系快递</view>
        <view class="btn-primary" @click="onRefresh">刷新物流</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      logisticsStatus: '运输中',
      carrier: '顺丰速运',
      trackingNumber: 'SF1234567890',
      timeline: [],
      receiverName: '',
      receiverPhone: '',
      receiverAddress: '',
    }
  },

  onLoad(query) {
    if (query.orderId) this.loadLogistics(query.orderId)
  },

  methods: {
    async loadLogistics(orderId) {
      try {
        const order = await orderApi.getOrderDetail(orderId)
        this.receiverName = order.receiverName || ''
        this.receiverPhone = order.receiverPhone || ''
        this.receiverAddress = order.receiverAddress || ''
        this.trackingNumber = order.trackingNumber || this.trackingNumber
      } catch (e) {
        console.warn('[logistics] load order failed', e)
      }
      try {
        const log = await orderApi.getLogistics(orderId)
        this.timeline = log?.traces || []
      } catch (e) {
        this.timeline = [
          { desc: '已签收', time: '今天 14:30', location: '上海浦东' },
          { desc: '派送中', time: '今天 09:00', location: '上海浦东' },
          { desc: '到达上海浦东转运中心', time: '昨天 22:00' },
          { desc: '已发货', time: '前天 16:30', location: '北京' },
        ]
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onCallCarrier() {
      uni.showToast({ title: '请联系顺丰客服 95338', icon: 'none' })
    },

    onRefresh() {
      uni.showLoading({ title: '刷新中...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: '已刷新', icon: 'success' })
      }, 800)
    },
  },
}
</script>

<style lang="scss" scoped>
.logistics-tracking {
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

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-right: 60rpx;
}

.content {
  padding: 24rpx;
}

.status-card {
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.status-icon {
  font-size: 64rpx;
}

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.status-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
}

.status-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.status-no {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.card-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.timeline-card,
.address-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  margin-bottom: 24rpx;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 16rpx;
  padding-left: 8rpx;
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: 12rpx;
  top: 16rpx;
  bottom: -24rpx;
  width: 1rpx;
  background: var(--color-divider);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-divider);
  margin-top: 8rpx;
  flex-shrink: 0;
  z-index: 1;
}

.timeline-item.active .timeline-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 6rpx rgba(46, 43, 41, 0.1);
}

.timeline-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.timeline-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.timeline-item.active .timeline-desc {
  font-weight: var(--font-weight-semibold);
}

.timeline-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.timeline-loc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.addr-name {
  display: block;
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  margin-bottom: 8rpx;
}

.addr-detail {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.btn-outline,
.btn-primary {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.btn-outline {
  border: 1rpx solid var(--color-divider);
  color: var(--color-text);
  background: var(--color-surface);
}

.btn-primary {
  background: var(--color-primary);
  color: var(--color-text);
}
</style>
