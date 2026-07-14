<template>
  <view class="home">
    <!-- 顶部自定义导航栏 -->
    <view class="navbar">
      <view class="navbar-search" @click="goSearch">
        <u-icon name="search" color="#9A948C" size="18"></u-icon>
        <text class="navbar-search-placeholder">Search for products, brands...</text>
      </view>
      <view class="navbar-icon" @click="onScan">
        <u-icon name="camera-fill" color="#2E2B29" size="22"></u-icon>
      </view>
      <view class="navbar-icon" @click="goMessages">
        <u-icon name="bell-fill" color="#2E2B29" size="22"></u-icon>
        <view v-if="unreadCount > 0" class="navbar-badge">{{ unreadCount }}</view>
      </view>
    </view>

    <!-- 滚动内容 -->
    <scroll-view
      scroll-y
      class="home-scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <!-- ① Banner 轮播 -->
      <view v-if="banners.length" class="banner">
        <u-swiper
          :list="banners"
          key-name="image"
          :autoplay="true"
          interval="3000"
          indicator
          circular
          @click="onBannerClick"
        ></u-swiper>
      </view>

      <!-- ② 金刚区：4 大产品线 + 4 个运营入口 -->
      <view class="kingkong">
        <view
          v-for="item in kingkongList"
          :key="item.id"
          class="kingkong-item"
          @click="onKingkongClick(item)"
        >
          <view class="kingkong-icon" :style="{ background: item.bg }">
            <text class="kingkong-emoji">{{ item.icon }}</text>
          </view>
          <text class="kingkong-label">{{ item.label }}</text>
        </view>
      </view>

      <!-- ③ 限时特惠 / 营销活动 -->
      <view class="promo" @click="goPromo">
        <view class="promo-tag">LIMITED</view>
        <text class="promo-title">Festive Picks · Holiday Edit</text>
        <text class="promo-subtitle">Up to 30% off selected care & gear</text>
        <view class="promo-arrow">→</view>
      </view>

      <!-- ④ 推荐流 / 猜你喜欢 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">For Your Companion</text>
          <text class="section-more" @click="goCategory">View All</text>
        </view>
        <view class="product-grid">
          <view
            v-for="p in recommend"
            :key="p.id"
            class="product-card"
            @click="goDetail(p.id)"
          >
            <image :src="p.image" class="product-image" mode="aspectFill" />
            <view v-if="p.ip" class="product-ip" :class="`tag-${p.ip.toLowerCase()}`">{{ p.ip }}</view>
            <text class="product-name text-ellipsis-2">{{ p.name }}</text>
            <view class="product-price-row">
              <text class="price">${{ p.price }}</text>
              <text v-if="p.regularPrice && p.regularPrice > p.price" class="price-original">${{ p.regularPrice }}</text>
            </view>
          </view>
        </view>
        <view v-if="loadingMore" class="loading-more">Loading...</view>
        <view v-else-if="noMore" class="loading-more">— No more —</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { productApi } from '@/api'
import { useProductStore } from '@/store'

export default {
  data() {
    return {
      refreshing: false,
      loadingMore: false,
      noMore: false,
      page: 1,
      unreadCount: 0,
      banners: [
        // 示例 Banner（实际应由 WP 后台或 CPT 提供）
        { image: 'https://picsum.photos/750/360?random=1', title: 'MILO 探险家' },
        { image: 'https://picsum.photos/750/360?random=2', title: 'LUNA 策展家' }
      ],
      kingkongList: [
        { id: 'care', label: 'CARE 洗护', icon: '🧴', bg: 'rgba(219,201,138,0.15)' },
        { id: 'gear', label: 'GEAR 出行', icon: '🎒', bg: 'rgba(171,185,173,0.15)' },
        { id: 'play', label: 'PLAY 玩具', icon: '🎾', bg: 'rgba(179,138,90,0.15)' },
        { id: 'home', label: 'HOME 家居', icon: '🏡', bg: 'rgba(217,180,176,0.15)' },
        { id: 'subscribe', label: '订阅中心', icon: '🔁', bg: 'rgba(219,201,138,0.15)' },
        { id: 'vip', label: '会员俱乐部', icon: '👑', bg: 'rgba(179,138,90,0.15)' },
        { id: 'coupon', label: '领券中心', icon: '🎟️', bg: 'rgba(171,185,173,0.15)' },
        { id: 'flash', label: '限时特惠', icon: '⚡', bg: 'rgba(217,180,176,0.15)' }
      ],
      recommend: []
    }
  },

  onLoad() {
    this.loadRecommend(true)
  },

  onShow() {
    // 拉取未读消息数（预留）
  },

  methods: {
    async loadRecommend(reset = false) {
      if (reset) {
        this.page = 1
        this.noMore = false
        this.recommend = []
      }
      try {
        const list = await productApi.getProductList({
          page: this.page,
          per_page: 20,
          orderby: 'popularity'
        })
        // 转换数据为页面展示格式
        const mapped = list.map((p) => ({
          id: p.id,
          name: p.name,
          image: p.images?.[0]?.src || '',
          price: p.price,
          regularPrice: p.regular_price,
          ip: this.detectIP(p)
        }))
        this.recommend.push(...mapped)
        this.noMore = list.length < 20
        this.page += 1
      } catch (e) {
        console.error('[home] load error', e)
      }
    },

    detectIP(p) {
      // 简单从 tags 提取 IP 标识
      const tags = p.tags?.map((t) => t.name) || []
      const ipMap = { MILO: 'MILO', LUNA: 'LUNA', ATLAS: 'ATLAS', OLIVE: 'OLIVE' }
      for (const tag of tags) {
        if (ipMap[tag.toUpperCase()]) return ipMap[tag.toUpperCase()]
      }
      return null
    },

    async onRefresh() {
      this.refreshing = true
      try {
        await this.loadRecommend(true)
      } finally {
        this.refreshing = false
      }
    },

    async onLoadMore() {
      if (this.loadingMore || this.noMore) return
      this.loadingMore = true
      try {
        await this.loadRecommend(false)
      } finally {
        this.loadingMore = false
      }
    },

    onKingkongClick(item) {
      // 根据金刚区类型跳转
      if (['care', 'gear', 'play', 'home'].includes(item.id)) {
        uni.switchTab({ url: '/pages/tabbar/category' })
        // 通过 globalData 传递选中分类
        getApp().globalData.categoryTab = item.id
      } else if (item.id === 'coupon') {
        uni.navigateTo({ url: '/pages/user/coupons' })
      } else {
        uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    },

    onBannerClick(idx) {
      uni.showToast({ title: `Banner ${idx + 1}`, icon: 'none' })
    },

    goSearch() {
      uni.navigateTo({ url: '/pages/goods/search' })
    },

    goMessages() {
      uni.navigateTo({ url: '/pages/user/messages' })
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    goCategory() {
      uni.switchTab({ url: '/pages/tabbar/category' })
    },

    goPromo() {
      uni.navigateTo({ url: '/pages/goods/list?promo=1' })
    },

    onScan() {
      // #ifdef APP-PLUS
      uni.scanCode({
        success: (res) => {
          if (res.result) {
            uni.showToast({ title: `识别: ${res.result.slice(0, 20)}`, icon: 'none' })
            // 实际项目中根据 URL 解析跳转
          }
        },
        fail: () => {
          uni.showToast({ title: '未识别到有效二维码', icon: 'none' })
        }
      })
      // #endif
      // #ifdef H5
      uni.showToast({ title: '请使用 APP 扫码', icon: 'none' })
      // #endif
    }
  }
}
</script>

<style lang="scss" scoped>
.home {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.navbar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  padding-top: env(safe-area-inset-top);
}

.navbar-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--color-background);
  border-radius: var(--radius-pill);
  padding: 16rpx 24rpx;
}

.navbar-search-placeholder {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.navbar-icon {
  position: relative;
  margin-left: 24rpx;
}

.navbar-badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  background: var(--color-danger);
  color: #fff;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  text-align: center;
  line-height: 28rpx;
}

.home-scroll {
  flex: 1;
}

.banner {
  padding: 16rpx 24rpx;

  :deep(u-swiper) {
    border-radius: var(--radius-lg);
    overflow: hidden;
  }
}

.kingkong {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  padding: 24rpx;
  background: var(--color-surface);
  margin: 0 24rpx;
  border-radius: var(--radius-lg);
}

.kingkong-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.kingkong-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.kingkong-emoji {
  font-size: 44rpx;
}

.kingkong-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.promo {
  position: relative;
  display: flex;
  flex-direction: column;
  margin: 24rpx;
  padding: 32rpx;
  background: linear-gradient(135deg, #2E2B29 0%, #4A4540 100%);
  color: #F6F2EE;
  border-radius: var(--radius-lg);
}

.promo-tag {
  display: inline-block;
  width: fit-content;
  padding: 4rpx 12rpx;
  background: var(--color-primary);
  color: #2E2B29;
  font-size: 20rpx;
  font-weight: bold;
  border-radius: var(--radius-sm);
  margin-bottom: 16rpx;
}

.promo-title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 8rpx;
}

.promo-subtitle {
  font-size: var(--font-size-sm);
  opacity: 0.7;
}

.promo-arrow {
  position: absolute;
  right: 32rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 48rpx;
  opacity: 0.5;
}

.section {
  padding: 24rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.section-more {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.product-card {
  position: relative;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16rpx;
  overflow: hidden;
}

.product-image {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.product-ip {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  padding: 4rpx 10rpx;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  font-weight: var(--font-weight-medium);
}

.product-name {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  margin: 16rpx 0 8rpx;
  line-height: 1.4;
  height: 72rpx;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.loading-more {
  text-align: center;
  padding: 32rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
