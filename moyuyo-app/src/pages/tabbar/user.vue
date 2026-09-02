<template>
  <view class="user">
    <view class="header">
      <view v-if="userStore.isLoggedIn" class="user-info" @click="goProfile">
        <image :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" />
        <view class="info">
          <text class="name">
            {{ userStore.userInfo?.nickname || userStore.userInfo?.email || $t('userCenter.defaultNickname') }}
          </text>
          <text class="email">{{ userStore.userInfo?.email }}</text>
          <text class="member-level">{{ memberLevel }}</text>
        </view>
        <text class="arrow luc-chevron-right" />
      </view>
      <view v-else class="login-prompt" @click="goLogin">
        <image :src="defaultAvatar" class="avatar" />
        <view class="login-text">
          <text class="name">{{ $t('userCenter.loginRegister') }}</text>
          <text class="email">{{ $t('userCenter.loginSubtitle') }}</text>
        </view>
        <view class="login-btn">{{ $t('userCenter.loginBtn') }}</view>
      </view>
    </view>

    <!-- 会员卡片：点击整张卡片跳转到会员中心 -->
    <view v-if="userStore.isLoggedIn" class="vip-card" @click="goMembership">
      <view class="vip-bg" />
      <view class="vip-content">
        <text class="vip-title">{{ $t('userCenter.vipTitle') }}</text>
        <text class="vip-points">Points: {{ points.toLocaleString() }}</text>
        <text class="vip-tip">{{ $t('userCenter.vipTip') }}</text>
      </view>
      <text class="vip-arrow luc-chevron-right" />
    </view>

    <!-- 钱包区域 -->
    <view v-if="userStore.isLoggedIn" class="wallet-area card">
      <view class="wallet-grid">
        <view class="wallet-item" @click="goWallet">
          <text class="wallet-num">${{ walletBalance }}</text>
          <text class="wallet-label">{{ $t('userCenter.walletBalance') }}</text>
        </view>
        <view class="wallet-item" @click="goPoints">
          <text class="wallet-num">{{ points.toLocaleString() }}</text>
          <text class="wallet-label">{{ $t('userCenter.walletPoints') }}</text>
        </view>
        <view class="wallet-item" @click="goCoupons">
          <text class="wallet-num">{{ couponCount }}{{ $t('coupons.unit') }}</text>
          <text class="wallet-label">{{ $t('userCenter.walletCoupons') }}</text>
        </view>
        <view class="wallet-item" @click="goGiftCards">
          <text class="wallet-num">{{ giftCardCount }}{{ $t('coupons.unit') }}</text>
          <text class="wallet-label">{{ $t('userCenter.walletGiftCards') }}</text>
        </view>
      </view>
    </view>

    <!-- 关注 / 粉丝 / 收藏 / 足迹 入口（封装为 social-grid 组件） -->
    <social-grid :items="socialItems" @click="onSocialTap" />

    <!-- 订单宫格 -->
    <view class="card order-card">
      <view class="card-header">
        <text class="card-title">{{ $t('userCenter.orderTitle') }}</text>
        <text class="card-more" @click="goOrders">
          {{ $t('userCenter.orderAll') }}
          <text class="luc luc-chevron-right" />
        </text>
      </view>
      <view class="order-grid">
        <view
          v-for="item in orderTypesLabel"
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
        v-for="(f, i) in featuresLabel"
        :key="i"
        class="feature-item"
        @click="onFeatureClick(f)">
        <text class="feature-icon luc" :class="$luc(f.icon)" />
        <text class="feature-label">{{ f.label }}</text>
        <text class="feature-arrow luc luc-chevron-right" />
      </view>
    </view>

    <view class="footer">
      <text>{{ $t('userCenter.footerBrand') }}</text>
      <text>{{ $t('userCenter.footerSlogan') }}</text>
    </view>
  </view>
</template>

<script>
import { useUserStore, useCartStore } from '@/store'
import { i18n } from '@/i18n'
import { memberApi, couponApi, giftCardApi, orderApi, communityApi } from '@/api'
import followApi from '@/api/follow'
import browseApi from '@/api/browsingHistory'
// social-grid 是 src/components 下的自建组件,正常情况下 easycom.autoscan 会自动注册;
// vite-plugin-uni H5 模式下偶尔会扫不到 kebab-case 引用,这里显式 import 兜底
import SocialGrid from '@/components/social-grid/social-grid.vue'

export default {
  pageTitleKey: 'pageTitle.tabbarUser',

  // 显式注册 social-grid 组件,避免 easycom.autoscan 在 vite-plugin-uni H5 模式下偶尔未扫到
  components: { SocialGrid },
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
      // i18n locale 版本号:locale 切换时自增,触发依赖 i18n 的 computed 重算
      localeVersion: 0,
      // 订单宫格只存结构与图标,badge 由 loadOrderBadges 异步回填
      // label 通过 orderTypesLabel computed 从 i18n 注入(响应 locale 切换)
      orderTypes: [
        { value: 'PENDING_PAY', icon: '💳', badge: 0 },
        { value: 'CART', icon: '🛒', badge: 0 },
        { value: 'PENDING_RECEIVE', icon: '🚚', badge: 0 },
        { value: 'COMPLETED', icon: '⭐', badge: 0 },
      ],
      // 功能入口只存 id 与图标,label 通过 featuresLabel computed 从 i18n 注入
      features: [
        { id: 'checkin', icon: 'calendar' },
        { id: 'missions', icon: 'target' },
        { id: 'invite', icon: 'user-plus' },
        { id: 'membership', icon: 'crown' },
        { id: 'address', icon: 'map-pin' },
        { id: 'pets', icon: 'paw-print' },
        { id: 'favorites', icon: 'heart' },
        { id: 'history', icon: 'footprints' },
        { id: 'help', icon: 'help-circle' },
        { id: 'settings', icon: 'settings' },
        { id: 'about', icon: 'sparkles' },
      ],
    }
  },

  computed: {
    userStore() {
      return useUserStore()
    },
    /**
     * 订单宫格渲染数据:把 data.orderTypes 拷一份并按 locale 注入 label
     * (保持 data 中只放非文案字段,locale 变化时 computed 自动重算)
     */
    orderTypesLabel() {
      void this.localeVersion
      return this.orderTypes.map((t) => ({
        ...t,
        label: i18n.t(`userCenter.orderStatus.${t.value}`),
      }))
    },
    /**
     * 功能入口渲染数据:同 orderTypesLabel 思路,按 id 取 userCenter.featureXxx 文案
     */
    featuresLabel() {
      void this.localeVersion
      return this.features.map((f) => ({
        ...f,
        label: i18n.t(`userCenter.feature${f.id.charAt(0).toUpperCase()}${f.id.slice(1)}`),
      }))
    },
    /**
     * 社交宫格数据源:把原来散落的 4 个 @click 入口聚合到一个数组里
     * icon / bg 用于组件内渲染图标徽章,url 决定点击行为
     */
    socialItems() {
      void this.localeVersion
      return [
        {
          key: 'following',
          label: i18n.t('userCenter.socialFollowing'),
          value: this.followingCount,
          icon: 'user-plus',
          url: '/pages/user/follow-list?mode=following',
        },
        {
          key: 'followers',
          label: i18n.t('userCenter.socialFollowers'),
          value: this.followerCount,
          icon: 'users',
          url: '/pages/user/follow-list?mode=followers',
        },
        {
          key: 'collection',
          label: i18n.t('userCenter.socialCollection'),
          value: this.collectCount,
          icon: 'bookmark',
          url: '/pages/user/post-collection',
        },
        {
          key: 'history',
          label: i18n.t('userCenter.socialHistory'),
          value: this.historyCount,
          icon: 'footprints',
          url: '/pages/user/browsing-history',
        },
      ]
    },
    memberLevel() {
      if (!this.memberInfo) return ''
      void this.localeVersion
      return i18n.t(`userCenter.memberLevel.${this.memberInfo.level}`) || this.memberInfo.level
    },
    growthPercent() {
      if (!this.memberInfo) return 0
      const total = 5000
      const current = this.memberInfo.growthValue || 0
      return Math.min(100, Math.round((current / total) * 100))
    },
  },

  onShow() {
    // 首次进入时订阅 i18n locale 变化,触发依赖文案的 computed 重算
    if (!this._unsubLocale) {
      this._unsubLocale = i18n.subscribe(() => {
        this.localeVersion += 1
      })
    }
    if (this.userStore.isLoggedIn) {
      this.loadMemberInfo()
      this.loadWalletExtras()
      this.loadSocialCounts()
      this.loadOrderBadges()
    }
  },

  onUnload() {
    if (this._unsubLocale) {
      this._unsubLocale()
      this._unsubLocale = null
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
        // 关注/粉丝:后端已返回 Page<Map>,从 total 取真实数(避免 size=1 + length=1 误判)
        // 收藏/足迹:从 IPage.total 取
        const tasks = [
          followApi
            .listFollowing({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? (Array.isArray(r) ? r.length : 0)))
            .catch(() => 0),
          followApi
            .listFollowers({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? (Array.isArray(r) ? r.length : 0)))
            .catch(() => 0),
          communityApi
            .getCollectedPosts({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? 0))
            .catch(() => 0),
          browseApi
            .getBrowsingHistory({ page: 1, size: 1 })
            .then((r) => Number(r?.total ?? 0))
            .catch(() => 0),
        ]
        const [following, followers, collect, history] = await Promise.all(tasks)
        this.followingCount = following
        this.followerCount = followers
        this.collectCount = collect
        this.historyCount = history
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

    /** 加载订单宫格各状态角标数量；购物车角标从 cart store 取 */
    async loadOrderBadges() {
      const cartStore = useCartStore()
      const statuses = this.orderTypes.filter((t) => t.value !== 'CART').map((t) => t.value)
      try {
        // 订单状态角标：逐个调订单列表接口
        const orderResults = await Promise.all(
          statuses.map((s) =>
            orderApi
              .getOrderList({ status: s, page: 1, size: 1 })
              .then((r) => r?.total || 0)
              .catch(() => 0),
          ),
        )
        // 把 CART 插回原位置（用 cart store 的 totalQuantity）
        let orderIdx = 0
        this.orderTypes = this.orderTypes.map((t) => {
          if (t.value === 'CART') {
            return { ...t, badge: cartStore.totalQuantity || 0 }
          }
          return { ...t, badge: orderResults[orderIdx++] || 0 }
        })
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
      if (type === 'CART') {
        uni.navigateTo({ url: '/pages/cart/index' })
        return
      }
      uni.navigateTo({ url: `/pages/order/list?type=${type || 'all'}` })
    },

    /**
     * 社交宫格点击事件:social-grid 子组件 emit('tap', item)
     * 未登录时,关注/粉丝跳登录,其它直接展示"待上线"提示
     */
    onSocialTap(item) {
      if (!item || !item.url) {
        uni.showToast({ title: i18n.t('userCenter.comingSoon'), icon: 'none' })
        return
      }
      // 关注/粉丝需要登录态;足迹/收藏未登录时本地有数据也能展示,这里不强校验
      const needLogin = item.key === 'following' || item.key === 'followers'
      if (needLogin && !this.userStore.isLoggedIn) {
        return uni.navigateTo({ url: '/pages/user/login' })
      }
      uni.navigateTo({ url: item.url })
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
        settings: '/pages/user/settings',
        about: '/pages/user/about',
      }
      if (map[f.id]) {
        uni.navigateTo({ url: map[f.id] })
      } else {
        uni.showToast({ title: i18n.t('userCenter.comingSoon'), icon: 'none' })
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
      // 跳到礼品卡管理页(已注册: pages/user/gift-cards.vue)
      uni.navigateTo({ url: '/pages/user/gift-cards' })
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
  /* 状态栏安全区：原公式只覆盖 iOS safe-area，APP 端（Android）需要再加 status-bar-height */
  padding-top: calc(48rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
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

/* 右上角箭头：放在 vip-bg 之上、vip-content 之外的视觉锚点，提示卡片可点击 */
.vip-arrow {
  position: absolute;
  right: 24rpx;
  bottom: 32rpx;
  font-size: 36rpx;
  color: #f6f2ee;
  opacity: 0.7;
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
