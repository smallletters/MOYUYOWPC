<template>
  <view class="favorites">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">我的收藏</text>
      <view class="header-btn" @click="onToggleView">
        <text class="grid-icon">⊞</text>
      </view>
    </view>

    <!-- 筛选标签栏 -->
    <scroll-view class="filter-bar" scroll-x show-scrollbar="false">
      <view
        v-for="tab in filterTabs"
        :key="tab.value"
        class="filter-tab"
        :class="{ active: activeTab === tab.value }"
        @click="onTabChange(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </scroll-view>

    <!-- 商品网格 -->
    <view class="product-grid">
      <view
        v-for="item in filteredList"
        :key="item.id"
        class="product-card"
        @click="goDetail(item.id)"
      >
        <view class="product-image-wrap">
          <image :src="item.image" mode="aspectFill" class="product-image" />
          <!-- 状态标签 -->
          <text
            v-for="badge in item.badges"
            :key="badge"
            class="badge"
            :class="badgeClass(badge)">
            {{ badge }}
          </text>
          <!-- 收藏按钮 -->
          <view class="fav-btn" @click.stop="toggleFav(item)">
            <text class="fav-icon" :class="{ 'fav-active': item.isFav }"><text class="luc luc-heart"></text></text>
          </view>
        </view>
        <view class="product-info">
          <text class="product-name">{{ item.name }}</text>
          <text class="product-price">¥{{ item.price }}</text>
        </view>
      </view>
    </view>

    <!-- 底部统计 -->
    <view class="footer-stat">
      <text class="stat-text">共收藏 {{ filteredList.length }} 件商品</text>
    </view>
  </view>
</template>

<script>
import { cartApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'all',
      filterTabs: [
        { label: '全部', value: 'all' },
        { label: '降价', value: 'priceDown' },
        { label: '上新', value: 'newArrival' },
        { label: '库存紧张', value: 'lowStock' },
      ],
      products: [],
    }
  },

  computed: {
    filteredList() {
      if (this.activeTab === 'all') return this.products
      const filterMap = {
        priceDown: (item) => item.badges.includes('降价'),
        newArrival: (item) => item.badges.includes('上新'),
        lowStock: (item) => item.badges.includes('库存紧张'),
      }
      const filterFn = filterMap[this.activeTab]
      return filterFn ? this.products.filter(filterFn) : this.products
    },
  },

  onLoad() {
    this.loadFavorites()
  },

  methods: {
    async loadFavorites() {
      try {
        const res = await cartApi.getFavorites()
        const list = res.data || res
        this.products = (list.items || list || []).map((f) => ({
          id: f.id,
          name: f.name || '',
          price: f.price || 0,
          image: f.image || '',
          badges: f.badges || [],
          isFav: true,
        }))
      } catch (e) {
        this.products = []
      }
    },

    goBack() {
      uni.navigateBack()
    },

    onToggleView() {
      uni.showToast({ title: '视图切换', icon: 'none' })
    },

    onTabChange(value) {
      this.activeTab = value
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    async toggleFav(item) {
      try {
        if (item.isFav) {
          await cartApi.removeFavorite(item.id)
          item.isFav = false
          uni.showToast({ title: '已取消收藏', icon: 'none' })
        } else {
          await cartApi.addFavorite(item.id)
          item.isFav = true
          uni.showToast({ title: '已收藏', icon: 'none' })
        }
      } catch (e) {
        uni.showToast({ title: '操作失败', icon: 'none' })
      }
    },

    badgeClass(badge) {
      if (badge === '降价') return 'badge-danger'
      if (badge === '库存紧张') return 'badge-warning'
      if (badge === '上新') return 'badge-primary'
      return ''
    },
  },
}
</script>

<style lang="scss" scoped>
.favorites {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 48rpx;
}

/* 顶部导航栏 */
.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-background);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-btn {
  position: absolute;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.header-btn:first-child {
  left: 16rpx;
}

.header-btn:last-child {
  right: 16rpx;
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-text);
  line-height: 1;
}

.grid-icon {
  font-size: 36rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

/* 筛选标签栏 */
.filter-bar {
  display: flex;
  flex-wrap: nowrap;
  padding: 20rpx 24rpx;
  white-space: nowrap;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.filter-bar::-webkit-scrollbar {
  display: none;
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 24rpx;
  margin-right: 16rpx;
  border-radius: var(--radius-sm);
  background: var(--color-divider);
  font-size: 26rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: all 0.18s ease;
  box-sizing: border-box;
}

.filter-tab.active {
  background: var(--color-primary);
  color: #ffffff;
}

/* 商品网格 */
.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  padding: 0 20rpx 16rpx;
}

.product-card {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: transform 0.15s ease;
}

.product-card:active {
  transform: scale(0.98);
}

.product-image-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
}

.product-image {
  width: 100%;
  height: 100%;
}

/* 状态标签 */
.badge {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36rpx;
  padding: 0 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: var(--font-weight-semibold);
  color: #ffffff;
  line-height: 1;
  white-space: nowrap;
}

.badge-danger {
  background: var(--color-danger);
}

.badge-warning {
  background: #ff9500;
}

.badge-primary {
  background: var(--color-primary);
}

/* 收藏按钮 */
.fav-btn {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.3);
}

.fav-icon {
  font-size: 32rpx;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1;
  transition: color 0.18s ease;
}

.fav-icon.fav-active {
  color: var(--color-danger);
}

/* 商品信息 */
.product-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  padding: 16rpx 16rpx 20rpx;
}

.product-name {
  font-size: 26rpx;
  line-height: 1.3;
  color: var(--color-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-price {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

/* 底部统计 */
.footer-stat {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0 32rpx;
}

.stat-text {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}
</style>
