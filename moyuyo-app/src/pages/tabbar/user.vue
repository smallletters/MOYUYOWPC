<template>
  <view class="user">
    <view class="header">
      <view v-if="userStore.isLoggedIn" class="user-info" @click="goProfile">
        <image :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" />
        <view class="info">
          <text class="name">{{ userStore.userInfo?.first_name || 'User' }}</text>
          <text class="email">{{ userStore.userInfo?.email }}</text>
          <text class="member-level">{{ memberLevel }}</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view v-else class="login-prompt" @click="goLogin">
        <image :src="defaultAvatar" class="avatar" />
        <view class="login-text">
          <text class="name">Sign In</text>
          <text class="email">Login to enjoy more benefits</text>
        </view>
        <view class="login-btn">Login</view>
      </view>
    </view>

    <!-- 会员卡片 -->
    <view v-if="userStore.isLoggedIn" class="vip-card">
      <view class="vip-bg"></view>
      <view class="vip-content">
        <text class="vip-title">MOYUYO Member</text>
        <text class="vip-points">Points: 1,280</text>
        <text class="vip-tip">Earn 5x points on this order</text>
      </view>
    </view>

    <!-- 订单宫格 -->
    <view class="card order-card">
      <view class="card-header">
        <text class="card-title">My Orders</text>
        <text class="card-more" @click="goOrders">All ›</text>
      </view>
      <view class="order-grid">
        <view
          v-for="item in orderTypes"
          :key="item.value"
          class="order-item"
          @click="goOrders(item.value)"
        >
          <text class="order-icon">{{ item.icon }}</text>
          <text class="order-label">{{ item.label }}</text>
          <view v-if="item.badge > 0" class="order-badge">{{ item.badge }}</view>
        </view>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="card feature-card">
      <view
        v-for="(f, i) in features"
        :key="i"
        class="feature-item"
        @click="onFeatureClick(f)"
      >
        <text class="feature-icon">{{ f.icon }}</text>
        <text class="feature-label">{{ f.label }}</text>
        <text class="feature-arrow">›</text>
      </view>
    </view>

    <view class="footer">
      <text>MOYUYO ATELIER v1.0.0</text>
      <text>Every Journey Together.</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store'

export default {
  data() {
    return {
      defaultAvatar: 'https://i.pravatar.cc/100?img=20',
      orderTypes: [
        { value: 'pending', label: 'To Pay', icon: '💳', badge: 0 },
        { value: 'processing', label: 'To Ship', icon: '📦', badge: 0 },
        { value: 'shipped', label: 'Shipped', icon: '🚚', badge: 0 },
        { value: 'review', label: 'To Review', icon: '⭐', badge: 0 }
      ],
      features: [
        { id: 'address', label: 'Shipping Address', icon: '📍' },
        { id: 'coupons', label: 'My Coupons', icon: '🎟️' },
        { id: 'wishlist', label: 'Wishlist', icon: '❤️' },
        { id: 'pets', label: 'My Pets', icon: '🐾' },
        { id: 'devices', label: 'Login Devices', icon: '📱' },
        { id: 'security', label: 'Account Security', icon: '🔒' },
        { id: 'settings', label: 'Settings', icon: '⚙️' },
        { id: 'about', label: 'About MOYUYO', icon: '✨' }
      ]
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    memberLevel() {
      // 根据用户积分判断会员等级
      return 'Gold Member'
    }
  },

  methods: {
    goLogin() {
      uni.navigateTo({ url: '/pages/user/login' })
    },

    goProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },

    goOrders(type) {
      uni.navigateTo({ url: `/pages/order/list?type=${type || 'all'}` })
    },

    onFeatureClick(f) {
      const map = {
        address: '/pages/user/address',
        devices: '/pages/user/devices',
        security: '/pages/user/security',
        pets: '/pages/pet/profile',
        settings: '/pages/user/settings'
      }
      if (map[f.id]) {
        uni.navigateTo({ url: map[f.id] })
      } else {
        uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.user {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 64rpx;
}

.header {
  background: var(--color-surface);
  padding: 48rpx 24rpx;
  padding-top: calc(48rpx + env(safe-area-inset-top));
}

.user-info, .login-prompt {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: var(--color-background);
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.email {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.member-level {
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
  margin-top: 4rpx;
}

.login-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.login-btn {
  padding: 12rpx 32rpx;
  background: var(--color-primary);
  color: var(--color-text);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}

.arrow {
  font-size: 48rpx;
  color: var(--color-text-tertiary);
}

.vip-card {
  position: relative;
  margin: 24rpx;
  border-radius: var(--radius-lg);
  overflow: hidden;
  height: 200rpx;
}

.vip-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #2E2B29 0%, #4A4540 100%);
}

.vip-content {
  position: relative;
  padding: 32rpx;
  color: #F6F2EE;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.vip-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
}

.vip-points {
  font-size: var(--font-size-md);
  color: var(--color-primary);
}

.vip-tip {
  font-size: var(--font-size-xs);
  opacity: 0.7;
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.card-more {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.order-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.order-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 0;
}

.order-icon {
  font-size: 48rpx;
}

.order-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.order-badge {
  position: absolute;
  top: 0;
  right: 32rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  background: var(--color-danger);
  color: #fff;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  text-align: center;
  line-height: 32rpx;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.feature-item:last-child {
  border-bottom: none;
}

.feature-icon {
  font-size: 36rpx;
}

.feature-label {
  flex: 1;
  font-size: var(--font-size-base);
}

.feature-arrow {
  color: var(--color-text-tertiary);
  font-size: 32rpx;
}

.footer {
  text-align: center;
  padding: 48rpx 24rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
</style>
