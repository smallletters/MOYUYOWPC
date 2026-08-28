<template>
  <view class="favorites">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" aria-label="返回" @click="goBack">
        <text class="header-btn-icon">
          <text class="luc luc-arrow-left" />
        </text>
      </view>
      <text class="header-title">我的收藏</text>
      <!-- 右上角:管理模式切换 -->
      <view class="header-btn header-action" :class="{ active: editing }" @click="onToggleEdit">
        <text class="header-btn-text">{{ editing ? '完成' : '管理' }}</text>
      </view>
    </view>

    <!-- 筛选标签栏 -->
    <scroll-view
      v-if="!editing"
      class="filter-bar"
      scroll-x
      show-scrollbar="false">
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

    <!-- 加载态 -->
    <view v-if="loading && products.length === 0" class="empty-tip">
      <text class="empty-tip-text">{{ $t('common.loading') }}</text>
    </view>

    <!-- 空状态 -->
    <view v-else-if="!loading && products.length === 0" class="empty-tip">
      <text class="empty-tip-icon">♡</text>
      <text class="empty-tip-text">还没有收藏的商品</text>
      <view class="empty-tip-btn" @click="goShopping">去逛逛</view>
    </view>

    <!-- 商品列表(网格,支持管理态加勾选框) -->
    <view v-else class="product-grid">
      <view
        v-for="item in filteredList"
        :key="item.id"
        class="product-card"
        :class="{ 'is-selected': editing && item.selected }"
        @click="onCardClick(item)"
      >
        <!-- 管理态:左上角勾选框 -->
        <view
          v-if="editing"
          class="select-box"
          :class="{ checked: item.selected }"
          aria-label="选择"
          @click.stop="onSelectItem(item)"
        >
          <text v-if="item.selected" class="select-box-tick">✓</text>
        </view>

        <view class="product-image-wrap">
          <image
            :src="item.image || defaultImage"
            mode="aspectFill"
            lazy-load
            class="product-image"
            @error="onImageError(item)"
          />
          <!-- 状态标签(基于实际数据动态计算) -->
          <text
            v-for="badge in item.badges"
            :key="badge"
            class="badge"
            :class="badgeClass(badge)">
            {{ badge }}
          </text>
          <!-- 非管理态下显示收藏心形,点击直接取消收藏 -->
          <view
            v-if="!editing"
            class="fav-btn"
            aria-label="取消收藏"
            @click.stop="toggleFav(item)">
            <text class="fav-icon" :class="{ 'fav-active': item.isFav }">
              {{ item.isFav ? '❤' : '♡' }}
            </text>
          </view>
        </view>

        <view class="product-info">
          <text class="product-name">{{ item.name || '商品已下架' }}</text>
          <view class="product-price-row">
            <text class="product-price">${{ formatPrice(item.price) }}</text>
            <text
              v-if="item.originalPrice && item.originalPrice > item.price"
              class="product-original"
            >
              ${{ formatPrice(item.originalPrice) }}
            </text>
          </view>
          <text v-if="item.favoritedAt" class="product-time">收藏于 {{ item.favoritedAt }}</text>
        </view>
      </view>

      <!-- 数据为 0 的当前 Tab 提示 -->
      <view
        v-if="filteredList.length === 0 && products.length > 0"
        class="empty-tip empty-tip-inline"
      >
        <text class="empty-tip-text">该分类下暂无收藏</text>
      </view>
    </view>

    <!-- 底部统计 / 管理操作栏 -->
    <view v-if="products.length > 0" class="footer-bar safe-area-bottom">
      <template v-if="editing">
        <!-- 管理态:全选 + 批量删除 -->
        <view class="footer-left" @click="onSelectAll">
          <view class="select-box" :class="{ checked: isAllSelected, partial: isPartialSelected }">
            <text v-if="isAllSelected" class="select-box-tick">✓</text>
          </view>
          <text class="footer-left-text">全选</text>
        </view>
        <view class="footer-right">
          <view
            class="footer-btn footer-btn-danger"
            :class="{ disabled: selectedCount === 0 }"
            @click="onBatchRemove"
          >
            删除{{ selectedCount > 0 ? `(${selectedCount})` : '' }}
          </view>
        </view>
      </template>
      <template v-else>
        <text class="stat-text">{{ $t('favorites.totalCount', { count: products.length }) }}</text>
      </template>
    </view>
  </view>
</template>

<script>
import { cartApi, productApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'all',
      loading: false,
      editing: false,
      // 拉到的原始收藏关联(用于记录收藏时间)
      favoriteRecords: [],
      products: [],
      defaultImage:
        'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxIDEiPjxyZWN0IHdpZHRoPSIxIiBoZWlnaHQ9IjEiIGZpbGw9IiNlYWVjZDMiLz48L3N2Zz4=',
      filterTabs: [
        { label: '全部', value: 'all' },
        { label: '降价', value: 'priceDown' },
        { label: '上新', value: 'newArrival' },
        { label: '库存紧张', value: 'lowStock' },
      ],
    }
  },

  computed: {
    /** 当前 Tab 过滤后的列表 */
    filteredList() {
      if (this.activeTab === 'all') return this.products
      const map = {
        priceDown: (item) => item.badges.includes('降价'),
        newArrival: (item) => item.badges.includes('上新'),
        lowStock: (item) => item.badges.includes('库存紧张'),
      }
      const fn = map[this.activeTab]
      return fn ? this.products.filter(fn) : this.products
    },
    /** 选中数量(管理态) */
    selectedCount() {
      return this.products.filter((p) => p.selected).length
    },
    /** 是否全选 */
    isAllSelected() {
      if (!this.products.length) return false
      return this.products.every((p) => p.selected)
    },
    /** 是否部分选中(显示半选状态) */
    isPartialSelected() {
      const cnt = this.selectedCount
      return cnt > 0 && cnt < this.products.length
    },
  },

  onLoad() {
    this.loadFavorites()
  },

  // 每次回到页面都重新拉取
  onShow() {
    // 退出管理态以免状态错乱
    if (this.editing) this.editing = false
    this.loadFavorites()
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.loadFavorites().finally(() => uni.stopPullDownRefresh())
  },

  methods: {
    /**
     * 加载收藏列表:
     * 后端 /api/v1/favorites 返回 [{id, productId, createTime}] 关联
     * 再用 productId 并发拉取 /api/v1/products/{id} 拿详情,组装展示数据
     */
    async loadFavorites() {
      this.loading = true
      try {
        // request.js 已解包外层 envelope,res 直接是后端 payload
        const res = await cartApi.getFavorites()
        // 后端可能返回数组,也可能返回 IPage { records }
        const records = Array.isArray(res) ? res : res?.records || res?.items || []
        this.favoriteRecords = records
        // productId 去重,避免收藏量极大时并发请求阻塞
        const productIds = Array.from(
          new Set(records.map((r) => r.productId).filter(Boolean)),
        ).slice(0, 100)
        if (!productIds.length) {
          this.products = []
          return
        }
        const results = await Promise.allSettled(
          productIds.map((pid) => productApi.getProductDetail(pid)),
        )
        // 关联收藏时间(用同 productId 第一条记录的 createTime)
        const favTimeMap = new Map()
        records.forEach((r) => {
          if (r.productId && r.createTime && !favTimeMap.has(r.productId)) {
            favTimeMap.set(r.productId, r.createTime)
          }
        })
        this.products = productIds
          .map((pid, idx) => {
            const r = results[idx]
            if (r.status !== 'fulfilled' || !r.value) return null
            const detail = r.value
            const price = parseFloat(detail.price) || 0
            const originalPrice = parseFloat(detail.originalPrice) || 0
            const stock = parseInt(detail.stock, 10) || 0
            // 动态计算 badges
            const badges = []
            if (originalPrice > price) badges.push('降价')
            // 上新:上架 30 天内
            if (detail.createTime) {
              const created = new Date(String(detail.createTime).replace(/-/g, '/'))
              if (!isNaN(created.getTime()) && Date.now() - created.getTime() < 30 * 86400000) {
                badges.push('上新')
              }
            }
            if (stock > 0 && stock <= 10) badges.push('库存紧张')
            return {
              id: pid,
              name: detail.name || '',
              price,
              originalPrice,
              image:
                detail.mainImage || (Array.isArray(detail.images) && detail.images[0]?.url) || '',
              badges,
              isFav: true,
              selected: false,
              stock,
              favoritedAt: this.formatDate(favTimeMap.get(pid)),
            }
          })
          .filter(Boolean)
      } catch (e) {
        console.warn('[favorites] load failed', e)
        this.products = []
      } finally {
        this.loading = false
      }
    },

    /** 图片加载失败 → 用默认占位图 */
    onImageError(item) {
      item.image = this.defaultImage
    },

    /** 价格格式化 */
    formatPrice(value) {
      const n = parseFloat(value) || 0
      return n.toFixed(2)
    },

    /** 收藏时间格式化为简短字符串 */
    formatDate(dt) {
      if (!dt) return ''
      const d = typeof dt === 'string' ? new Date(dt.replace(/-/g, '/')) : new Date(dt)
      if (isNaN(d.getTime())) return ''
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },

    /** 进入/退出管理模式 */
    onToggleEdit() {
      this.editing = !this.editing
      // 进入管理态时清掉勾选状态
      if (this.editing) {
        this.products.forEach((p) => (p.selected = false))
      }
    },

    /** 商品卡片点击:
     * - 管理态:切换勾选
     * - 正常态:进入详情页 */
    onCardClick(item) {
      if (this.editing) {
        this.onSelectItem(item)
      } else {
        this.goDetail(item.id)
      }
    },

    /** 单个勾选 */
    onSelectItem(item) {
      item.selected = !item.selected
    },

    /** 全选/取消全选 */
    onSelectAll() {
      const allSelected = this.isAllSelected
      this.products.forEach((p) => (p.selected = !allSelected))
    },

    /** 批量删除选中项 */
    async onBatchRemove() {
      if (this.selectedCount === 0) return
      const selected = this.products.filter((p) => p.selected)
      // 二次确认
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '确认删除',
          content: `将取消收藏 ${selected.length} 件商品?`,
          confirmText: '删除',
          confirmColor: '#c96e5f',
          success: (r) => resolve(r.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) return
      // 并发调用取消收藏(单个失败不影响其他)
      const tasks = selected.map((s) => cartApi.removeFavorite(s.id))
      const results = await Promise.allSettled(tasks)
      const successIds = selected
        .filter((_, i) => results[i]?.status === 'fulfilled')
        .map((s) => s.id)
      this.products = this.products.filter((p) => !successIds.includes(p.id))
      // 如果全部删完了,自动退出管理态
      if (this.products.length === 0) this.editing = false
      uni.showToast({
        title: `已删除 ${successIds.length} 件`,
        icon: 'success',
      })
    },

    /** 单个商品取消收藏 */
    async toggleFav(item) {
      if (!item.isFav) return
      try {
        await cartApi.removeFavorite(item.id)
        item.isFav = false
        // 从列表中移除(动画效果可选)
        this.products = this.products.filter((p) => p.id !== item.id)
        uni.showToast({ title: '已取消收藏', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: e?.message || '操作失败', icon: 'none' })
      }
    },

    /** 跳转详情 */
    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },

    /** 返回上一页 */
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/tabbar/user' })
      }
    },

    /** 去首页逛逛 */
    goShopping() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },

    /** 标签 class */
    badgeClass(badge) {
      if (badge === '降价') return 'badge-danger'
      if (badge === '库存紧张') return 'badge-warning'
      if (badge === '上新') return 'badge-primary'
      return ''
    },

    /** 切换筛选 Tab */
    onTabChange(value) {
      this.activeTab = value
    },
  },
}
</script>

<style lang="scss" scoped>
.favorites {
  min-height: 100vh;
  background: var(--color-background);
  /* 底部操作栏 + 安全区 */
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

/* ===== 顶部导航 ===== */
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
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 56rpx;
  padding: 0 20rpx;
  border-radius: var(--radius-sm);
  transition: background-color 0.18s ease;
}

.header-btn:first-child {
  left: 16rpx;
  padding: 0;
  width: 56rpx;
}

.header-btn:last-child {
  right: 16rpx;
}

.header-btn:active {
  background: var(--color-divider);
}

.header-btn-icon {
  font-size: 40rpx;
  color: var(--color-text);
  line-height: 1;
}

.header-btn-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.header-btn.active .header-btn-text {
  color: var(--color-primary-dark);
}

.header-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  letter-spacing: -0.02em;
}

/* ===== 筛选标签栏 ===== */
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
  border-radius: var(--radius-pill);
  background: var(--color-divider);
  font-size: 26rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  flex-shrink: 0;
  transition: all 0.18s ease;
}

.filter-tab.active {
  background: var(--color-primary);
  color: #ffffff;
}

/* ===== 勾选框 ===== */
.select-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  background: var(--color-surface);
  color: transparent;
  font-size: 24rpx;
  line-height: 1;
  box-sizing: border-box;
  transition: all 0.18s ease;
  flex-shrink: 0;
}

.select-box.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.select-box.partial {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.select-box-tick {
  color: var(--color-text);
  font-weight: var(--font-weight-bold);
}

/* ===== 商品网格 ===== */
.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  padding: 0 20rpx;
}

.product-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 2rpx solid transparent;
  border-radius: var(--radius-md);
  overflow: hidden;
  transition:
    transform 0.15s ease,
    border-color 0.18s ease;
}

.product-card.is-selected {
  border-color: var(--color-primary);
}

.product-card:active {
  transform: scale(0.98);
}

.product-image-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--color-divider);
}

.product-image {
  width: 100%;
  height: 100%;
}

/* 管理态勾选框:左上角 */
.product-card .select-box {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  z-index: 2;
}

/* 状态标签 */
.badge {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
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
  z-index: 2;
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

/* 单卡片收藏按钮(非管理态) */
.fav-btn {
  position: absolute;
  bottom: 12rpx;
  right: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  z-index: 2;
}

.fav-icon {
  font-size: 28rpx;
  line-height: 1;
  color: rgba(255, 255, 255, 0.9);
  transition:
    color 0.18s ease,
    transform 0.18s ease;
}

.fav-icon.fav-active {
  color: #34c759;
  transform: scale(1.1);
}

/* 商品信息 */
.product-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
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
  min-height: 68rpx;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.product-price {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary-dark);
}

.product-original {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

.product-time {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
}

/* ===== 空状态 / 加载态 ===== */
.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  padding: 160rpx 32rpx 40rpx;
  color: var(--color-text-tertiary);
}

.empty-tip.empty-tip-inline {
  padding: 80rpx 32rpx;
}

.empty-tip-icon {
  font-size: 96rpx;
  opacity: 0.35;
  color: var(--color-text-tertiary);
}

.empty-tip-text {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
}

.empty-tip-btn {
  margin-top: 16rpx;
  padding: 16rpx 56rpx;
  background: var(--color-primary);
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  border-radius: 9999rpx;
}

/* ===== 底部统计 / 操作栏 ===== */
.footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
  padding-bottom: env(safe-area-inset-bottom);
}

.stat-text {
  width: 100%;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.footer-left-text {
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.footer-right {
  display: flex;
  align-items: center;
}

.footer-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 200rpx;
  height: 72rpx;
  padding: 0 32rpx;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  border-radius: 9999rpx;
  transition: opacity 0.18s ease;
}

.footer-btn.disabled {
  opacity: 0.4;
}

.footer-btn-danger {
  background: var(--color-danger);
  color: #ffffff;
}

/* iPhone 安全区 */
.safe-area-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
