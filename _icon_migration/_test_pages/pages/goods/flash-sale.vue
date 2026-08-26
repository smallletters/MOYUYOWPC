<template>
  <view class="flash-sale">
    <!-- 顶部导航栏 -->
    <view class="nav-header">
      <view class="nav-back" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="nav-title">限时抢购</text>
      <view class="nav-back" @tap="toggleNotify">
        <text class="notify-icon"><text class="luc luc-bell"></text></text>
      </view>
    </view>

    <!-- 秒杀头部横幅 -->
    <view class="flash-banner">
      <view class="banner-left">
        <text class="banner-title">限时秒杀</text>
        <text class="banner-sub">超值好物 限量抢购</text>
      </view>
      <view class="banner-right">
        <text class="countdown-label">本场距结束</text>
        <view class="countdown">
          <text class="countdown-digit">{{ countdown.hours }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ countdown.minutes }}</text>
          <text class="countdown-sep">:</text>
          <text class="countdown-digit">{{ countdown.seconds }}</text>
        </view>
      </view>
    </view>

    <!-- 场次切换 -->
    <view class="session-tabs">
      <view
        v-for="session in sessions"
        :key="session.id"
        class="session-tab"
        :class="{ active: activeSession === session.id }"
        :style="{
          background:
            activeSession === session.id ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.12)',
          color: activeSession === session.id ? '#ffffff' : 'rgba(255,255,255,0.7)',
        }"
        @tap="activeSession = session.id"
      >
        <text
          class="session-dot"
          :style="{ background: activeSession === session.id ? '#ffffff' : 'transparent' }"
        />
        <text>{{ session.label }}</text>
        <text class="session-status">{{ session.status }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 秒杀商品网格 -->
      <view class="product-grid">
        <view
          v-for="product in flashProducts"
          :key="product.id"
          class="product-card"
          :class="{ soldOut: product.soldOut }"
          :style="{ opacity: product.soldOut ? 0.55 : 1 }"
          @tap="handleProductTap(product)"
        >
          <view class="product-image">
            <view class="img-placeholder" :style="{ background: product.color }" />
            <view class="flash-badge">秒杀</view>
            <view v-if="product.soldOut" class="sold-out-overlay">
              <text>已售罄</text>
            </view>
          </view>
          <view class="product-info">
            <text class="product-name">{{ product.name }}</text>
            <view class="price-row">
              <text class="flash-price">¥{{ product.flashPrice }}</text>
              <text class="original-price">¥{{ product.originalPrice }}</text>
            </view>
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: product.soldPercent + '%' }" />
              <text class="progress-text">已抢 {{ product.soldPercent }}%</text>
            </view>
            <view class="product-actions">
              <view
                class="buy-btn"
                :class="{ disabled: product.soldOut }"
                @tap.stop="handleFlashBuy(product)"
              >
                <text>{{ product.soldOut ? '已售罄' : '抢购' }}</text>
              </view>
              <text class="limit-text">每人限{{ product.limit }}件</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 下一场预告 -->
      <view class="next-session">
        <view class="next-left">
          <text class="next-icon">ⓘ</text>
          <view class="next-info">
            <text class="next-title">下一场 20:00 开始</text>
            <text class="next-desc">更多爆款即将开放，敬请期待</text>
          </view>
        </view>
        <view class="next-countdown">
          <text class="next-label">距开始</text>
          <view class="next-digits">
            <text class="next-digit">{{ nextCountdown.hours }}</text>
            <text class="next-sep">:</text>
            <text class="next-digit">{{ nextCountdown.minutes }}</text>
            <text class="next-sep">:</text>
            <text class="next-digit">{{ nextCountdown.seconds }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { flashSaleApi } from '@/api'

export default {
  data() {
    return {
      activeSession: 'ongoing',
      sessions: [
        { id: 'upcoming', label: '即将开始', status: '预告' },
        { id: 'ongoing', label: '进行中', status: '抢购中' },
        { id: 'ended', label: '已结束', status: '已结束' },
      ],
      countdown: { hours: '02', minutes: '34', seconds: '56' },
      nextCountdown: { hours: '07', minutes: '25', seconds: '04' },
      flashProducts: [],
      timerId: null,
    }
  },
  onLoad() {
    this.loadFlashSales()
    this.startCountdown()
  },
  onUnload() {
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  },
  methods: {
    async loadFlashSales() {
      try {
        const res = await flashSaleApi.getFlashSales({ session: this.activeSession })
        const list = res.data || res
        this.flashProducts = (list.items || list || []).map((p) => ({
          id: p.id,
          name: p.name || '',
          flashPrice: p.flashPrice || p.price || 0,
          originalPrice: p.originalPrice || 0,
          soldPercent: p.soldPercent || 0,
          limit: p.limit || 1,
          soldOut: p.soldOut || false,
          color: p.color || 'linear-gradient(135deg, #cfe5ff, #007aff)',
        }))
      } catch (e) {
        this.flashProducts = []
      }
    },
    goBack() {
      uni.navigateBack()
    },
    toggleNotify() {
      uni.showToast({ title: '已设置开抢提醒', icon: 'success' })
    },
    handleProductTap(product) {
      if (!product.soldOut) {
        uni.navigateTo({ url: `/pages/goods/detail?id=${product.id}` })
      }
    },
    async handleFlashBuy(product) {
      if (product.soldOut) return
      try {
        await flashSaleApi.buyFlashSale(product.id, 1)
        uni.showToast({ title: `抢购「${product.name}」成功！`, icon: 'success' })
      } catch (e) {
        uni.showToast({ title: '抢购失败，请重试', icon: 'none' })
      }
    },
    startCountdown() {
      let totalSec = 2 * 3600 + 34 * 60 + 56
      let nextTotalSec = 7 * 3600 + 25 * 60 + 4
      this.timerId = setInterval(() => {
        if (totalSec > 0) {
          totalSec--
          this.countdown.hours = String(Math.floor(totalSec / 3600)).padStart(2, '0')
          this.countdown.minutes = String(Math.floor((totalSec % 3600) / 60)).padStart(2, '0')
          this.countdown.seconds = String(totalSec % 60).padStart(2, '0')
        }
        if (nextTotalSec > 0) {
          nextTotalSec--
          this.nextCountdown.hours = String(Math.floor(nextTotalSec / 3600)).padStart(2, '0')
          this.nextCountdown.minutes = String(Math.floor((nextTotalSec % 3600) / 60)).padStart(
            2,
            '0',
          )
          this.nextCountdown.seconds = String(nextTotalSec % 60).padStart(2, '0')
        }
      }, 1000)
    },
  },
}
</script>

<style scoped lang="scss">
.flash-sale {
  min-height: 100vh;
  background: var(--background);
}
.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  height: 88rpx;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600));
}
.nav-back {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  color: #ffffff;
  font-size: 40rpx;
}
.notify-icon {
  font-size: 36rpx;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}
.flash-banner {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16rpx 40rpx 24rpx;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600));
}
.banner-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
  display: block;
}
.banner-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 8rpx;
  display: block;
}
.banner-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
}
.countdown-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.7);
}
.countdown {
  display: flex;
  align-items: center;
}
.countdown-digit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 80rpx;
  height: 80rpx;
  padding: 0 16rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.2);
  font-size: 44rpx;
  font-weight: 800;
  color: #ffffff;
}
.countdown-sep {
  font-size: 36rpx;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.8);
  margin: 0 4rpx;
}
.session-tabs {
  display: flex;
  gap: 16rpx;
  padding: 0 40rpx 32rpx;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600));
}
.session-tab {
  flex: 1;
  height: 72rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  font-size: 24rpx;
}
.session-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}
.session-status {
  font-size: 20rpx;
  font-weight: 400;
  opacity: 0.7;
}
.scroll {
  padding: 30rpx;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}
.product-card {
  border-radius: 24rpx;
  overflow: hidden;
  border: 2rpx solid var(--border);
  background: var(--card);
  box-shadow: var(--shadow-sm);
}
.product-image {
  aspect-ratio: 1;
  position: relative;
  overflow: hidden;
}
.img-placeholder {
  width: 100%;
  height: 100%;
}
.flash-badge {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
  background: var(--state-error);
}
.sold-out-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  color: #ffffff;
}
.product-info {
  padding: 20rpx;
}
.product-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 12rpx;
}
.flash-price {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--state-error);
}
.original-price {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--text-400);
}
.progress-bar {
  margin-top: 16rpx;
  height: 28rpx;
  border-radius: 999rpx;
  background: var(--background-200);
  overflow: hidden;
  position: relative;
}
.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, var(--state-error), var(--state-error-dark));
  transition: width 0.3s;
}
.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 700;
  color: var(--text-800);
}
.product-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.buy-btn {
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: var(--state-error);
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.buy-btn.disabled {
  background: var(--background-400);
  color: var(--text-400);
}
.limit-text {
  font-size: 20rpx;
  color: var(--text-400);
}
.next-session {
  margin: 40rpx 0 30rpx;
  border-radius: 24rpx;
  padding: 30rpx;
  background: var(--background-200);
  border: 2rpx solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.next-left {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}
.next-icon {
  font-size: 40rpx;
  color: var(--primary);
}
.next-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
}
.next-desc {
  font-size: 24rpx;
  color: var(--text-500);
  margin-top: 4rpx;
  display: block;
}
.next-countdown {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.next-label {
  font-size: 24rpx;
  color: var(--text-500);
}
.next-digits {
  display: flex;
  align-items: center;
}
.next-digit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52rpx;
  height: 52rpx;
  padding: 0 8rpx;
  border-radius: 8rpx;
  background: var(--foreground);
  color: var(--background-50);
  font-size: 28rpx;
  font-weight: 700;
}
.next-sep {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--foreground);
  margin: 0 4rpx;
}
.bottom-spacer {
  height: 40rpx;
}
</style>
