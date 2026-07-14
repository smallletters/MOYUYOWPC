<template>
  <view class="logistics">
    <view class="card status-card">
      <text class="status-title">In Transit</text>
      <text class="status-desc">Estimated delivery: 3-5 work days</text>
    </view>

    <view class="timeline">
      <view
        v-for="(t, i) in timeline"
        :key="i"
        class="timeline-item"
        :class="{ active: i === 0, completed: i > 0 }"
      >
        <view class="timeline-dot"></view>
        <view class="timeline-content">
          <text class="timeline-title">{{ t.title }}</text>
          <text class="timeline-desc">{{ t.desc }}</text>
          <text class="timeline-time">{{ t.time }}</text>
        </view>
      </view>
    </view>

    <view class="card info-card">
      <text class="info-title">Tracking Number</text>
      <text class="info-value">1Z999AA10123456784</text>
      <text class="info-title">Carrier</text>
      <text class="info-value">UPS</text>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      timeline: [
        { title: 'Out for Delivery', desc: 'Your package is on the delivery vehicle', time: 'Today 9:30 AM' },
        { title: 'Arrived at Local Facility', desc: 'NEW YORK, NY', time: 'Yesterday 8:15 PM' },
        { title: 'In Transit', desc: 'LOUISVILLE, KY', time: '2 days ago' },
        { title: 'Shipped', desc: 'MOYUYO Warehouse', time: '3 days ago' }
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
.logistics {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
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
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 32rpx 24rpx;
  margin-bottom: 16rpx;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 24rpx;
  padding-bottom: 32rpx;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-item::before {
  content: '';
  position: absolute;
  left: 16rpx;
  top: 32rpx;
  bottom: 0;
  width: 2rpx;
  background: var(--color-divider);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-dot {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: var(--color-divider);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.timeline-item.active .timeline-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 8rpx rgba(219, 201, 138, 0.2);
}

.timeline-item.completed .timeline-dot {
  background: var(--color-success);
}

.timeline-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.timeline-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.timeline-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.timeline-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.info-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.info-title {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.info-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}
</style>
