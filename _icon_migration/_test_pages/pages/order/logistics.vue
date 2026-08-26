<template>
  <view class="logistics">
    <view v-if="!logistics" class="loading">Loading...</view>

    <template v-else>
      <view class="card status-card">
        <text class="status-title">{{ statusLabel }}</text>
        <text v-if="logistics.carrier" class="status-desc">
          {{ logistics.carrier }} · {{ logistics.trackingNumber }}
        </text>
      </view>

      <view v-if="logistics.traces && logistics.traces.length" class="timeline">
        <view
          v-for="(t, i) in logistics.traces"
          :key="i"
          class="timeline-item"
          :class="{ active: i === 0, completed: i > 0 }"
        >
          <view class="timeline-dot" />
          <view class="timeline-content">
            <text class="timeline-title">{{ t.desc }}</text>
            <text v-if="t.location" class="timeline-desc">{{ t.location }}</text>
            <text class="timeline-time">{{ t.time }}</text>
          </view>
        </view>
      </view>

      <view v-if="logistics.carrier" class="card info-card">
        <text class="info-title">Tracking Number</text>
        <text class="info-value">{{ logistics.trackingNumber }}</text>
        <text class="info-title">Carrier</text>
        <text class="info-value">{{ logistics.carrier }}</text>
      </view>
    </template>
  </view>
</template>

<script>
import { orderApi } from '@/api'

export default {
  data() {
    return {
      orderId: null,
      logistics: null,
    }
  },

  computed: {
    statusLabel() {
      if (!this.logistics) return ''
      const map = { DELIVERED: 'Delivered', IN_TRANSIT: 'In Transit', PENDING: 'Pending Shipment' }
      return map[this.logistics.currentStatus] || 'In Transit'
    },
  },

  onLoad(query) {
    this.orderId = query.id
    this.loadLogistics()
  },

  methods: {
    async loadLogistics() {
      try {
        this.logistics = await orderApi.getLogistics(this.orderId)
      } catch (e) {
        console.error('[logistics] load error', e)
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.logistics {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}

.loading {
  text-align: center;
  padding: 64rpx;
  color: var(--color-text-tertiary);
}

.status-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 32rpx 24rpx;
  margin-bottom: 16rpx;
  text-align: center;
}

.status-title {
  display: block;
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

.status-desc {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.timeline {
  padding: 0 16rpx;
  margin-bottom: 16rpx;
}

.timeline-item {
  display: flex;
  gap: 16rpx;
  padding-bottom: 32rpx;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: 14rpx;
    top: 32rpx;
    bottom: 0;
    width: 2rpx;
    background: var(--color-divider);
  }

  &:last-child::before {
    display: none;
  }
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

.timeline-item.completed .timeline-dot {
  background: var(--color-primary);
}

.timeline-content {
  flex: 1;
}

.timeline-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-bottom: 4rpx;
}

.timeline-desc {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: 4rpx;
}

.timeline-time {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.info-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin: 0 0 16rpx;
}

.info-title {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 16rpx;
  &:first-child {
    margin-top: 0;
  }
}

.info-value {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-top: 4rpx;
}
</style>
