<template>
  <view class="devices">
    <view class="tip">
      You can sign in on up to 3 devices. Unwanted devices? Sign them out below.
    </view>

    <view
      v-for="d in devices"
      :key="d.id"
      class="card device-card"
      :class="{ current: d.isCurrent }"
    >
      <view class="device-info">
        <text class="device-icon">📱</text>
        <view class="device-meta">
          <text class="device-name">{{ d.name }} <text v-if="d.isCurrent" class="current-tag">Current</text></text>
          <text class="device-detail">{{ d.model }} · {{ d.location }}</text>
          <text class="device-detail">Last active: {{ d.lastActive }}</text>
        </view>
      </view>
      <view v-if="!d.isCurrent && d.trusted" class="trust" @click="onUntrust(d)">Trusted · Tap to remove</view>
      <view v-else-if="!d.isCurrent && !d.trusted" class="trust add" @click="onTrust(d)">Trust this device</view>
      <view v-if="!d.isCurrent" class="signout" @click="onSignOut(d)">Sign out</view>
    </view>
  </view>
</template>

<script>
import { setStorage, getStorage, STORAGE_KEYS } from '@/utils/storage'

export default {
  data() {
    return {
      devices: [
        { id: 1, name: 'iPhone 15 Pro', model: 'iOS 17.5', location: 'New York, US', lastActive: 'Now', isCurrent: true, trusted: true },
        { id: 2, name: 'MacBook Pro', model: 'Chrome 125', location: 'New York, US', lastActive: '2 hours ago', isCurrent: false, trusted: false },
        { id: 3, name: 'iPad Air', model: 'iPadOS 17', location: 'Los Angeles, US', lastActive: '3 days ago', isCurrent: false, trusted: false }
      ]
    }
  },

  methods: {
    onSignOut(d) {
      uni.showModal({
        title: `Sign out ${d.name}?`,
        success: (res) => {
          if (res.confirm) {
            this.devices = this.devices.filter((x) => x.id !== d.id)
            uni.showToast({ title: 'Signed out', icon: 'success' })
          }
        }
      })
    },

    onTrust(d) {
      d.trusted = true
      uni.showToast({ title: 'Marked as trusted', icon: 'success' })
    },

    onUntrust(d) {
      d.trusted = false
      uni.showToast({ title: 'Trust removed', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.devices {
  min-height: 100vh;
  background: var(--color-background);
  padding: 16rpx;
}

.tip {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  padding: 16rpx;
  line-height: 1.6;
}

.device-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.device-card.current {
  border: 2rpx solid var(--color-primary);
}

.device-info {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}

.device-icon {
  font-size: 48rpx;
}

.device-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.device-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.current-tag {
  display: inline-block;
  margin-left: 8rpx;
  padding: 2rpx 10rpx;
  background: var(--color-primary);
  color: var(--color-text);
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  font-weight: var(--font-weight-medium);
}

.device-detail {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.trust {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
  font-size: var(--font-size-sm);
  color: var(--color-success);
}

.trust.add {
  color: var(--color-text-tertiary);
}

.signout {
  margin-top: 8rpx;
  font-size: var(--font-size-sm);
  color: var(--color-danger);
}
</style>
