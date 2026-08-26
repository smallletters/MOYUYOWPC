<template>
  <view class="coupon-center">
    <!-- 深色顶部导航栏 -->
    <view class="nav-bar">
      <view class="nav-btn" @click="goBack">
        <text class="nav-back"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">领券中心</text>
      <view class="nav-btn" />
    </view>

    <!-- 限时抢券倒计时横幅 -->
    <view class="countdown-banner">
      <view class="countdown-left">
        <text class="countdown-icon"><text class="luc luc-zap"></text></text>
        <text class="countdown-label">限时领券</text>
      </view>
      <view class="countdown-right">
        <text class="countdown-hint">剩余</text>
        <view class="countdown-timer">
          <text class="timer-block">{{ countdownStr.h }}</text>
          <text class="timer-colon">:</text>
          <text class="timer-block">{{ countdownStr.m }}</text>
          <text class="timer-colon">:</text>
          <text class="timer-block">{{ countdownStr.s }}</text>
        </view>
      </view>
    </view>

    <!-- 优惠券分类 Tab -->
    <scroll-view class="tab-bar" scroll-x show-scrollbar="false">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ 'tab-active': activeTab === tab.value }"
        @click="onTabChange(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </scroll-view>

    <!-- 优惠券列表 -->
    <view class="coupon-list">
      <view
        v-for="coupon in filteredCoupons"
        :key="coupon.id"
        class="coupon-card"
        :class="{ claimed: coupon.claimed }"
      >
        <!-- 左侧面额区域 -->
        <view class="coupon-amount-area">
          <text class="coupon-type-label">{{ coupon.typeLabel }}</text>
          <view class="coupon-amount-row">
            <text v-if="coupon.currency" class="coupon-currency">{{ coupon.currency }}</text>
            <text class="coupon-amount" :class="coupon.claimed ? 'amount-claimed' : ''">
              {{ coupon.amount }}
            </text>
            <text v-if="coupon.unit" class="coupon-unit">{{ coupon.unit }}</text>
          </view>
        </view>
        <!-- 中间详情区域 -->
        <view class="coupon-info">
          <view class="coupon-name-row">
            <text class="coupon-name" :class="coupon.claimed ? 'name-claimed' : ''">
              {{ coupon.name }}
            </text>
            <text
              v-if="coupon.badge"
              class="coupon-badge"
              :style="{ background: coupon.badgeBg, color: coupon.badgeColor }"
            >
              {{ coupon.badge }}
            </text>
          </view>
          <text class="coupon-condition" :class="coupon.claimed ? 'text-claimed' : ''">
            {{ coupon.condition }}
          </text>
          <text class="coupon-validity" :class="coupon.claimed ? 'text-claimed' : ''">
            {{ coupon.validity }}
          </text>
        </view>
        <!-- 右侧按钮 -->
        <view class="coupon-action">
          <view
            class="claim-btn"
            :class="coupon.claimed ? 'btn-claimed' : 'btn-active'"
            @click="onClaim(coupon)"
          >
            <text>{{ coupon.claimed ? '已领取' : '领取' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部汇总栏 -->
    <view class="bottom-bar">
      <view class="bottom-summary">
        <text class="summary-icon"><text class="luc luc-ticket"></text></text>
        <text class="summary-text">
          已领取
          <text class="summary-count">{{ claimedCount }}</text>
          张优惠券
        </text>
      </view>
      <view class="use-btn" @click="onUse">
        <text>去使用</text>
      </view>
    </view>
  </view>
</template>

<script>
import { couponApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'all',
      totalSeconds: 2 * 3600 + 15 * 60 + 30,
      timerId: null,

      tabs: [
        { label: '全部', value: 'all' },
        { label: '新人券', value: 'new' },
        { label: '品类券', value: 'category' },
        { label: '满减券', value: 'discount' },
        { label: '限时券', value: 'flash' },
        { label: '会员专属', value: 'member' },
      ],

      coupons: [],
    }
  },

  computed: {
    filteredCoupons() {
      if (this.activeTab === 'all') return this.coupons
      return this.coupons.filter((c) => c.category === this.activeTab)
    },

    claimedCount() {
      return this.coupons.filter((c) => c.claimed).length
    },

    countdownStr() {
      const h = Math.floor(this.totalSeconds / 3600)
      const m = Math.floor((this.totalSeconds % 3600) / 60)
      const s = this.totalSeconds % 60
      return {
        h: String(h).padStart(2, '0'),
        m: String(m).padStart(2, '0'),
        s: String(s).padStart(2, '0'),
      }
    },
  },

  async onLoad() {
    this.startCountdown()
    await this.loadCoupons()
  },

  onUnload() {
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  },

  methods: {
    async loadCoupons() {
      try {
        const res = await couponApi.getAvailableCoupons()
        const list = res.data || res
        this.coupons = (list.items || list).map((c) => ({
          id: c.id,
          category: c.category || 'all',
          typeLabel: c.typeLabel || '',
          currency: c.currency || '¥',
          amount: c.amount || '',
          unit: c.unit || '',
          name: c.name || '',
          condition: c.condition || '',
          validity: c.validity || '',
          badge: c.badge || '',
          badgeBg: c.badgeBg || '',
          badgeColor: c.badgeColor || '',
          claimed: c.claimed || false,
        }))
      } catch (e) {
        this.coupons = []
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onTabChange(value) {
      this.activeTab = value
    },

    async onClaim(coupon) {
      if (coupon.claimed) return
      try {
        await couponApi.claimCoupon(coupon.id)
        coupon.claimed = true
        uni.showToast({ title: '领取成功', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '领取失败，请重试', icon: 'none' })
      }
    },

    onUse() {
      uni.showToast({ title: '前往使用', icon: 'none' })
    },

    startCountdown() {
      this.timerId = setInterval(() => {
        if (this.totalSeconds > 0) {
          this.totalSeconds--
        }
      }, 1000)
    },
  },
}
</script>

<style lang="scss" scoped>
.coupon-center {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 128rpx;
}

/* 深色导航栏 */
.nav-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-text);
}

.nav-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  color: var(--color-surface);
}

.nav-btn:first-child {
  left: 16rpx;
}

.nav-btn:last-child {
  right: 16rpx;
}

.nav-back {
  font-size: 48rpx;
  line-height: 1;
}

.nav-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-surface);
}

/* 倒计时横幅 */
.countdown-banner {
  margin: 24rpx 32rpx;
  padding: 24rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
}

.countdown-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.countdown-icon {
  font-size: 32rpx;
  animation: pulse-glow 2s ease-in-out infinite;
}

.countdown-label {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-surface);
}

.countdown-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.countdown-hint {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.7);
}

.countdown-timer {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.timer-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52rpx;
  height: 48rpx;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.6);
  color: var(--color-surface);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
}

.timer-colon {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-surface);
}

@keyframes pulse-glow {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

/* Tab 栏 */
.tab-bar {
  display: flex;
  flex-wrap: nowrap;
  padding: 0 32rpx 16rpx;
  white-space: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.tab-bar::-webkit-scrollbar {
  display: none;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 28rpx;
  margin-right: 16rpx;
  border-radius: var(--radius-pill);
  background: var(--color-divider);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.tab-item.tab-active {
  background: var(--color-primary);
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

/* 优惠券列表 */
.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 0 32rpx 24rpx;
}

.coupon-card {
  position: relative;
  display: flex;
  gap: 16rpx;
  padding: 28rpx 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.coupon-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: linear-gradient(180deg, var(--color-primary-light), var(--color-primary-dark));
  border-radius: 3rpx 0 0 3rpx;
}

.coupon-card.claimed::before {
  background: var(--color-divider);
}

.coupon-card.claimed {
  opacity: 0.55;
}

/* 左侧面额 */
.coupon-amount-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 120rpx;
  flex-shrink: 0;
}

.coupon-type-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.coupon-amount-row {
  display: flex;
  align-items: baseline;
  gap: 2rpx;
  margin-top: 4rpx;
}

.coupon-currency {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

.coupon-amount {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
  line-height: 1;
}

.coupon-amount.amount-claimed {
  color: var(--color-text-tertiary);
}

.coupon-unit {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

/* 中间详情 */
.coupon-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.coupon-name-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.coupon-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-name.name-claimed {
  color: var(--color-text-tertiary);
}

.coupon-badge {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 12rpx;
  border-radius: var(--radius-sm);
  font-size: 20rpx;
  font-weight: var(--font-weight-bold);
  flex-shrink: 0;
}

.coupon-condition {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: 8rpx;
}

.coupon-validity {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.text-claimed {
  color: var(--color-text-tertiary) !important;
}

/* 右侧按钮 */
.coupon-action {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.claim-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12rpx 28rpx;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  transition: all 0.15s ease;
}

.claim-btn:active {
  transform: scale(0.96);
}

.btn-active {
  background: var(--color-primary);
  color: var(--color-text);
}

.btn-claimed {
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}

/* 底部汇总栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 112rpx;
  padding: 0 40rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  padding-bottom: env(safe-area-inset-bottom);
}

.bottom-summary {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.summary-icon {
  font-size: 32rpx;
}

.summary-text {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.summary-count {
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

.use-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 44rpx;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  transition: all 0.15s ease;
}

.use-btn:active {
  transform: scale(0.96);
}
</style>
