<template>
  <view class="user">
    <view class="header">
      <view v-if="userStore.isLoggedIn" class="user-info" @click="goProfile">
        <image :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" />
        <view class="info">
          <text class="name">
            {{ userStore.userInfo?.nickname || userStore.userInfo?.email || 'User' }}
          </text>
          <text class="email">{{ userStore.userInfo?.email }}</text>
          <text class="member-level">{{ memberLevel }}</text>
        </view>
        <text class="arrow"><text class="luc luc-chevron-right" /></text>
      </view>
      <view v-else class="login-prompt" @click="goLogin">
        <image :src="defaultAvatar" class="avatar" />
        <view class="login-text">
          <text class="name">登录 / 注册</text>
          <text class="email">登录后享受更多会员权益</text>
        </view>
        <view class="login-btn">登录</view>
      </view>
    </view>

    <!-- 会员卡片 -->
    <view v-if="userStore.isLoggedIn" class="vip-card">
      <view class="vip-bg" />
      <view class="vip-content">
        <text class="vip-title">MOYUYO Member</text>
        <text class="vip-points">Points: {{ points.toLocaleString() }}</text>
        <text class="vip-tip">Earn 5x points on this order</text>
      </view>
    </view>

    <!-- 钱包区域 -->
    <view v-if="userStore.isLoggedIn" class="wallet-area card">
      <view class="wallet-grid">
        <view class="wallet-item" @click="goWallet">
          <text class="wallet-num">${{ walletBalance }}</text>
          <text class="wallet-label">余额</text>
        </view>
        <view class="wallet-item" @click="goPoints">
          <text class="wallet-num">{{ points.toLocaleString() }}</text>
          <text class="wallet-label">积分</text>
        </view>
        <view class="wallet-item" @click="goCoupons">
          <text class="wallet-num">{{ couponCount }}张</text>
          <text class="wallet-label">优惠券</text>
        </view>
        <view class="wallet-item" @click="goGiftCards">
          <text class="wallet-num">{{ giftCardCount }}张</text>
          <text class="wallet-label">礼品卡</text>
        </view>
      </view>
    </view>

    <!-- 关注 / 粉丝 入口（与 wallet 风格一致） -->
    <view class="card social-area">
      <view class="social-grid">
        <view class="social-item" @click="goFollowing">
          <text class="social-num">{{ followingCount }}</text>
          <text class="social-label">关注</text>
        </view>
        <view class="social-item" @click="goFollowers">
          <text class="social-num">{{ followerCount }}</text>
          <text class="social-label">粉丝</text>
        </view>
        <view class="social-item" @click="goCollection">
          <text class="social-num">{{ collectCount }}</text>
          <text class="social-label">收藏</text>
        </view>
        <view class="social-item" @click="goHistory">
          <text class="social-num">{{ historyCount }}</text>
          <text class="social-label">足迹</text>
        </view>
      </view>
    </view>

    <!-- 订单宫格 -->
    <view class="card order-card">
      <view class="card-header">
        <text class="card-title">我的订单</text>
        <text class="card-more" @click="goOrders">
          全部
          <text class="luc luc-chevron-right" />
        </text>
      </view>
      <view class="order-grid">
        <view
          v-for="item in orderTypes"
          :key="item.value"
          class="order-item"
          @click="goOrders(item.value)"
        >
          <text class="order-icon luc" :class="$luc(item.icon)" />
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
        @click="onFeatureClick(f)">
        <text class="feature-icon luc" :class="$luc(f.icon)" />
        <text class="feature-label">{{ f.label }}</text>
        <text class="feature-arrow"><text class="luc luc-chevron-right" /></text>
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
import { memberApi, couponApi, giftCardApi, orderApi } from '@/api'
import followApi from '@/api/follow'
import browseApi from '@/api/browsingHistory'

export default {
  data() {
    return {
      defaultAvatar: 'https://i.pravatar.cc/100?img=20',
      memberInfo: null,
      points: 0,
      walletBalance: 0,
      couponCount: 0,
      giftCardCount: 0,
      followingCount: 0,
      followerCount: 0,
      collectCount: 0,
      historyCount: 0,
      orderTypes: [
        { value: 'PENDING_PAY', label: '待付款', icon: '💳', badge: 0 },
        { value: 'PENDING_SHIP', label: '待发货', icon: '📦', badge: 0 },
        { value: 'PENDING_RECEIVE', label: '待收货', icon: '🚚', badge: 0 },
        { value: 'COMPLETED', label: '待评价', icon: '⭐', badge: 0 },
      ],
      features: [
        { id: 'checkin', label: '每日签到', icon: '📅' },
        { id: 'missions', label: '任务中心', icon: '🎯' },
        { id: 'invite', label: '邀请好友', icon: '🤝' },
        { id: 'membership', label: '会员中心', icon: '👑' },
        { id: 'address', label: '收货地址', icon: '📍' },
        { id: 'pets', label: '宠物档案', icon: '🐾' },
        { id: 'favorites', label: '我的收藏', icon: '❤' },
        { id: 'history', label: '浏览足迹', icon: '👣' },
        { id: 'help', label: '帮助中心', icon: '❓' },
        { id: 'about', label: '关于我们', icon: '✨' },
      ],
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    memberLevel() {
      if (!this.memberInfo) return ''
      const levelMap = {
        NORMAL: 'Member',
        SILVER: 'Silver Member',
        GOLD: 'Gold Member',
        PLATINUM: 'Platinum Member',
        DIAMOND: 'Diamond Member',
      }
      return levelMap[this.memberInfo.level] || this.memberInfo.level
    },
    growthPercent() {
      if (!this.memberInfo) return 0
      const total = 5000
      const current = this.memberInfo.growthValue || 0
      return Math.min(100, Math.round((current / total) * 100))
    },
  },

  onShow() {
    if (this.userStore.isLoggedIn) {
      this.loadMemberInfo()
      this.loadWalletExtras()
      this.loadSocialCounts()
      this.loadOrderBadges()
    }
  },

  methods: {
    async loadMemberInfo() {
      try {
        const info = await memberApi.getMemberInfo()
        this.memberInfo = info
        this.points = info.points || 0
        this.walletBalance = info.walletBalance || 0
      } catch (e) {
        console.warn('[user] load member info failed', e)
      }
    },

    async loadSocialCounts() {
      if (!this.userStore.isLoggedIn) return
      try {
        // 并行拉取关注 / 粉丝 / 收藏 / 足迹的总数
        const tasks = [
          followApi
            .listFollowing({ page: 1, size: 1 })
            .then((r) => (Array.isArray(r) ? r.length : 0))
            .catch(() => 0),
          followApi
            .listFollowers({ page: 1, size: 1 })
            .then((r) => (Array.isArray(r) ? r.length : 0))
            .catch(() => 0),
          browseApi
            .getBrowsingHistory({ page: 1, size: 1 })
            .then((r) => r?.total || 0)
            .catch(() => 0),
        ]
        const [f1, f2, hist] = await Promise.all(tasks)
        this.followingCount = f1
        this.followerCount = f2
        this.historyCount = hist
      } catch (e) {
        console.warn('[user] load social counts failed', e)
      }
    },

    /** 加载钱包附加数据：优惠券 / 礼品卡数量 */
    async loadWalletExtras() {
      try {
        const tasks = [
          couponApi
            .getMyCoupons('UNUSED')
            .then((r) => {
              // 后端返回 IPage,total 即未使用张数；老版本可能直接返回数组
              if (r && typeof r === 'object' && 'total' in r) return r.total || 0
              return Array.isArray(r) ? r.length : 0
            })
            .catch(() => 0),
          giftCardApi
            .getGiftCards({ page: 1, size: 1 })
            .then((r) => r?.total || 0)
            .catch(() => 0),
        ]
        const [couponCnt, giftCnt] = await Promise.all(tasks)
        this.couponCount = couponCnt
        this.giftCardCount = giftCnt
      } catch (e) {
        console.warn('[user] load wallet extras failed', e)
      }
    },

    /** 加载订单宫格各状态角标数量 */
    async loadOrderBadges() {
      const statuses = this.orderTypes.map((t) => t.value)
      try {
        const results = await Promise.all(
          statuses.map((s) =>
            orderApi
              .getOrderList({ status: s, page: 1, size: 1 })
              .then((r) => r?.total || 0)
              .catch(() => 0),
          ),
        )
        this.orderTypes = this.orderTypes.map((t, i) => ({ ...t, badge: results[i] || 0 }))
      } catch (e) {
        console.warn('[user] load order badges failed', e)
      }
    },

    goLogin() {
      uni.navigateTo({ url: '/pages/user/login' })
    },

    goProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },

    goOrders(type) {
      uni.navigateTo({ url: `/pages/order/list?type=${type || 'all'}` })
    },

    goFollowing() {
      if (!this.userStore.isLoggedIn) return uni.navigateTo({ url: '/pages/user/login' })
      uni.navigateTo({ url: '/pages/user/follow-list?mode=following' })
    },

    goFollowers() {
      if (!this.userStore.isLoggedIn) return uni.navigateTo({ url: '/pages/user/login' })
      uni.navigateTo({ url: '/pages/user/follow-list?mode=followers' })
    },

    goCollection() {
      uni.navigateTo({ url: '/pages/user/post-collection' })
    },

    goHistory() {
      uni.navigateTo({ url: '/pages/user/browsing-history' })
    },

    onFeatureClick(f) {
      const map = {
        checkin: '/pages/user/check-in',
        missions: '/pages/user/mission-center',
        invite: '/pages/user/invite',
        membership: '/pages/user/membership',
        address: '/pages/user/address',
        pets: '/pages/pet/profile',
        favorites: '/pages/user/favorites',
        history: '/pages/user/browsing-history',
        help: '/pages/user/help',
        about: '/pages/user/about',
      }
      if (map[f.id]) {
        uni.navigateTo({ url: map[f.id] })
      } else {
        uni.showToast({ title: 'Coming soon', icon: 'none' })
      }
    },

    goWallet() {
      uni.navigateTo({ url: '/pages/user/wallet' })
    },

    goPoints() {
      uni.navigateTo({ url: '/pages/user/points-shop' })
    },

    goCoupons() {
      uni.navigateTo({ url: '/pages/user/coupon-center' })
    },

    goGiftCards() {
      uni.showToast({ title: 'Gift cards coming soon', icon: 'none' })
    },

    /** 跳到每日签到 */
    goCheckin() {
      uni.navigateTo({ url: '/pages/user/check-in' })
    },

    /** 跳到任务中心 */
    goMissions() {
      uni.navigateTo({ url: '/pages/user/mission-center' })
    },

    /** 跳到邀请好友 */
    goInvite() {
      uni.navigateTo({ url: '/pages/user/invite' })
    },

    /** 跳到会员中心 */
    goMembership() {
      uni.navigateTo({ url: '/pages/user/membership' })
    },
  },
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

.user-info,
.login-prompt {
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
  box-shadow: 0 8rpx 32rpx rgba(0, 122, 255, 0.3);
}

.vip-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--color-primary, #007aff) 0%, #004fad 100%);
}

.vip-content {
  position: relative;
  padding: 32rpx;
  color: #f6f2ee;
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

.wallet-area {
  margin: 0 24rpx 16rpx;
}

.wallet-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8rpx;
  text-align: center;
}

.wallet-item {
  padding: 16rpx 0;
}

.wallet-num {
  display: block;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
  margin-bottom: 4rpx;
}

.wallet-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.card {
  background: var(--color-surface);
  border-radius: 24rpx;
  margin: 0 24rpx 16rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.08);
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
