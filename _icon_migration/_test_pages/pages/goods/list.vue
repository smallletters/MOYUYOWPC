<template>
  <view class="goods-list">
    <!-- 顶部排序栏 -->
    <view class="sort-bar">
      <view
        v-for="opt in sortOptions"
        :key="opt.value"
        class="sort-item"
        :class="{ active: sortBy === opt.value }"
        @click="onSortChange(opt.value)"
      >
        {{ opt.label }}
      </view>
      <view class="sort-item" @click="filterVisible = true">
        Filter
        <text v-if="filterCount > 0" class="filter-count">{{ filterCount }}</text>
      </view>
    </view>

    <!-- 筛选弹窗 -->
    <u-popup v-model:show="filterVisible" mode="right" width="80%">
      <view class="filter-panel">
        <view class="filter-title">Filters</view>
        <view class="filter-section">
          <text class="filter-label">Pet Type</text>
          <view class="filter-options">
            <view
              v-for="opt in petTypes"
              :key="opt.value"
              class="filter-chip"
              :class="{ active: filter.petType === opt.value }"
              @click="filter.petType = filter.petType === opt.value ? '' : opt.value"
            >
              {{ opt.label }}
            </view>
          </view>
        </view>
        <view class="filter-section">
          <text class="filter-label">IP</text>
          <view class="filter-options">
            <view
              v-for="opt in ipOptions"
              :key="opt"
              class="filter-chip"
              :class="{ active: filter.ip === opt }"
              @click="filter.ip = filter.ip === opt ? '' : opt"
            >
              {{ opt }}
            </view>
          </view>
        </view>
        <view class="filter-section">
          <text class="filter-label">Size</text>
          <view class="filter-options">
            <view
              v-for="opt in sizes"
              :key="opt"
              class="filter-chip"
              :class="{ active: filter.size === opt }"
              @click="filter.size = filter.size === opt ? '' : opt"
            >
              {{ opt }}
            </view>
          </view>
        </view>
        <view class="filter-section">
          <text class="filter-label">Price Range</text>
          <view class="filter-options">
            <view
              v-for="opt in priceRanges"
              :key="opt.value"
              class="filter-chip"
              :class="{ active: filter.priceRange === opt.value }"
              @click="filter.priceRange = filter.priceRange === opt.value ? '' : opt.value"
            >
              {{ opt.label }}
            </view>
          </view>
        </view>
        <view class="filter-footer">
          <view class="btn btn-outline" @click="resetFilter">Reset</view>
          <view class="btn btn-primary" @click="applyFilter">Apply</view>
        </view>
      </view>
    </u-popup>

    <!-- 商品网格 -->
    <scroll-view
      scroll-y
      class="scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <view class="product-grid">
        <view
          v-for="p in products"
          :key="p.id"
          class="product-card"
          @click="goDetail(p.id)">
          <image :src="p.image" class="product-image" mode="aspectFill" />
          <view v-if="p.ip" class="product-ip" :class="`tag-${p.ip.toLowerCase()}`">
            {{ p.ip }}
          </view>
          <text class="product-name text-ellipsis-2">{{ p.name }}</text>
          <view class="product-price-row">
            <text class="price">${{ p.price }}</text>
            <text v-if="p.regularPrice > p.price" class="price-original">
              ${{ p.regularPrice }}
            </text>
          </view>
        </view>
      </view>
      <view v-if="loading" class="loading">Loading...</view>
      <view v-else-if="noMore" class="loading">— No more —</view>
      <view v-if="!loading && products.length === 0" class="empty">No products found</view>
    </scroll-view>
  </view>
</template>

<script>
import { productApi } from '@/api'

export default {
  data() {
    return {
      sortBy: 'default',
      sortOptions: [
        { value: 'default', label: 'Default' },
        { value: 'popularity', label: 'Best Selling' },
        { value: 'price_asc', label: 'Price ↑' },
        { value: 'price_desc', label: 'Price ↓' },
        { value: 'date', label: 'Newest' },
      ],
      petTypes: [
        { value: 'dog', label: 'Dog' },
        { value: 'cat', label: 'Cat' },
        { value: 'other', label: 'Other' },
      ],
      ipOptions: ['MILO', 'LUNA', 'ATLAS', 'OLIVE', 'Classic'],
      sizes: ['XS', 'S', 'M', 'L', 'XL'],
      priceRanges: [
        { value: '0-20', label: 'Under $20' },
        { value: '20-50', label: '$20 - $50' },
        { value: '50-100', label: '$50 - $100' },
        { value: '100+', label: 'Over $100' },
      ],
      filter: {
        petType: '',
        ip: '',
        size: '',
        priceRange: '',
      },
      filterVisible: false,
      products: [],
      loading: false,
      refreshing: false,
      noMore: false,
      page: 1,
      keyword: '',
      category: '',
    }
  },

  computed: {
    filterCount() {
      return Object.values(this.filter).filter(Boolean).length
    },
  },

  onLoad(query) {
    this.keyword = query?.keyword || ''
    this.category = query?.category || ''
    this.loadProducts(true)
  },

  methods: {
    async loadProducts(reset = false) {
      if (reset) {
        this.page = 1
        this.noMore = false
        this.products = []
      }
      this.loading = true
      try {
        const params = {
          page: this.page,
          size: 20,
          sortBy: this.mapSortBy(),
          sortOrder: this.mapSortOrder(),
        }
        if (this.keyword) params.keyword = this.keyword
        if (this.category) params.categoryId = this.category
        if (this.filter.ip) {
          params.brandIpId = this.resolveIpId(this.filter.ip)
        }
        const res = await productApi.getProductList(params)
        const list = res.records || res || []
        const mapped = list.map((p) => ({
          id: p.id,
          name: p.name,
          image: p.mainImage || p.images?.[0]?.src || '',
          price: parseFloat(p.price) || 0,
          regularPrice: parseFloat(p.regularPrice || p.regular_price) || 0,
          ip: p.brandIpId ? this.resolveIpName(p.brandIpId) : null,
        }))
        this.products.push(...mapped)
        this.noMore = list.length < 20
        this.page += 1
      } catch (e) {
        console.error('[goods-list] error', e)
      } finally {
        this.loading = false
        this.refreshing = false
      }
    },

    mapSortBy() {
      const map = { default: 'createTime', popularity: 'sales', date: 'createTime' }
      return map[this.sortBy] || 'price'
    },

    mapSortOrder() {
      return this.sortBy === 'price_asc' ? 'asc' : this.sortBy === 'price_desc' ? 'desc' : 'desc'
    },

    resolveIpName(brandIpId) {
      const map = { 1: 'MILO', 2: 'LUNA', 3: 'ATLAS', 4: 'OLIVE' }
      return map[brandIpId] || null
    },

    resolveIpId(name) {
      const map = { MILO: 1, LUNA: 2, ATLAS: 3, OLIVE: 4 }
      return map[name?.toUpperCase()] || null
    },

    onSortChange(value) {
      this.sortBy = value
      this.loadProducts(true)
    },

    onRefresh() {
      this.refreshing = true
      this.loadProducts(true)
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.loadProducts(false)
    },

    resetFilter() {
      this.filter = { petType: '', ip: '', size: '', priceRange: '' }
    },

    applyFilter() {
      this.filterVisible = false
      this.loadProducts(true)
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },
  },
}
</script>

<style lang="scss" scoped>
.goods-list {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.sort-bar {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.sort-item {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  position: relative;
}

.sort-item.active {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.filter-count {
  display: inline-block;
  margin-left: 4rpx;
  padding: 0 8rpx;
  min-width: 32rpx;
  height: 32rpx;
  background: var(--color-primary);
  color: var(--color-text);
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  text-align: center;
  line-height: 32rpx;
}

.filter-panel {
  padding: 32rpx 24rpx;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.filter-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 24rpx;
}

.filter-section {
  margin-bottom: 32rpx;
}

.filter-label {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-bottom: 16rpx;
  display: block;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.filter-chip {
  padding: 12rpx 24rpx;
  font-size: var(--font-size-sm);
  background: var(--color-background);
  color: var(--color-text-secondary);
  border-radius: var(--radius-pill);
}

.filter-chip.active {
  background: var(--color-primary);
  color: var(--color-text);
}

.filter-footer {
  margin-top: auto;
  display: flex;
  gap: 16rpx;
}

.filter-footer .btn {
  flex: 1;
}

.scroll {
  flex: 1;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding: 16rpx;
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

.loading,
.empty {
  text-align: center;
  padding: 32rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
