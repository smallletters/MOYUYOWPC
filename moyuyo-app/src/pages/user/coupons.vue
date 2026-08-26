<template>
  <view class="coupons">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">我的优惠券</text>
    </view>

    <view class="tab-bar">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="activeTab = t.value"
      >
        {{ t.label }}
      </view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="filteredCoupons.length === 0" class="empty">
        <text class="empty-icon">🎟</text>
        <text class="empty-text">暂无优惠券</text>
      </view>

      <view v-else class="coupon-list">
        <view
          v-for="c in filteredCoupons"
          :key="c.id"
          class="coupon-card"
          :class="{ used: c.status === 'used' }"
        >
          <view class="coupon-left">
            <text class="coupon-amount">¥{{ c.amount }}</text>
            <text class="coupon-threshold">满{{ c.threshold }}可用</text>
          </view>
          <view class="coupon-right">
            <text class="coupon-name">{{ c.name }}</text>
            <text class="coupon-scope">{{ c.scope }}</text>
            <text class="coupon-expire">有效期至 {{ c.expire }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { couponApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'unused',
      tabs: [
        { value: 'unused', label: '未使用' },
        { value: 'used', label: '已使用' },
        { value: 'expired', label: '已过期' },
      ],
      coupons: [],
    }
  },

  computed: {
    filteredCoupons() {
      return this.coupons.filter((c) => c.status === this.activeTab)
    },
  },

  onShow() {
    this.loadCoupons()
  },

  methods: {
    async loadCoupons() {
      try {
        const list = await couponApi.getUserCoupons()
        this.coupons = Array.isArray(list) ? list : []
      } catch (e) {
        console.warn('[coupons] load failed', e)
      }
    },

    goBack() {
      uni.navigateBack()
    },
  },
}
</script>

<style lang="scss" scoped>
.coupons {
  min-height: 100vh;
  background: var(--color-background);
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

.tab-bar {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  position: relative;
}

.tab.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.content {
  padding: 24rpx;
}

.empty {
  padding: 96rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.coupon-card {
  display: flex;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.coupon-card.used {
  opacity: 0.5;
}

.coupon-left {
  width: 200rpx;
  padding: 24rpx 16rpx;
  background: var(--color-primary);
  color: var(--color-text);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.coupon-amount {
  font-size: 48rpx;
  font-weight: var(--font-weight-bold);
}

.coupon-threshold {
  font-size: var(--font-size-xs);
  margin-top: 4rpx;
}

.coupon-right {
  flex: 1;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.coupon-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.coupon-scope {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.coupon-expire {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: auto;
}
</style>