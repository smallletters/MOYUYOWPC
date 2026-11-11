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
        {{ $t('goodsList.filter') }}
        <text v-if="filterCount > 0" class="filter-count">{{ filterCount }}</text>
      </view>
    </view>

    <!-- 筛选弹窗 -->
    <u-popup v-model:show="filterVisible" mode="right" width="80%">
      <view class="filter-panel">
        <view class="filter-title">{{ $t('goodsList.filtersTitle') }}</view>
        <!-- 筛选条件区域独立滚动，保证底部操作按钮始终可见 -->
        <scroll-view scroll-y class="filter-body">
          <view class="filter-section">
            <text class="filter-label">{{ $t('goodsList.petType') }}</text>
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
            <text class="filter-label">{{ $t('goodsList.ip') }}</text>
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
            <text class="filter-label">{{ $t('goodsList.size') }}</text>
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
            <text class="filter-label">{{ $t('goodsList.priceRange') }}</text>
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
        </scroll-view>
        <view class="filter-footer">
          <view class="btn btn-outline" @click="resetFilter">{{ $t('goodsList.reset') }}</view>
          <view class="btn btn-primary" @click="applyFilter">{{ $t('goodsList.apply') }}</view>
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
          <!-- 图片容器按卡片宽度撑出 1:1 高度，规避 uni-image 固定 240px 高度在宽屏下只拉宽不拉高 -->
          <view class="product-image-wrap">
            <image :src="$img(p.image)" class="product-image" mode="aspectFill" />
          </view>
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
          <text v-if="p.campaignTag" class="campaign-tag">{{ p.campaignTag }}</text>
        </view>
      </view>
      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>
      <view v-else-if="noMore" class="loading">{{ $t('goodsList.noMore') }}</view>
      <view v-if="!loading && products.length === 0" class="empty">
        {{ $t('goodsList.empty') }}
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { productApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.goodsList',

  data() {
    return {
      sortBy: 'default',
      ipOptions: ['MILO', 'LUNA', 'ATLAS', 'OLIVE', 'Classic'],
      sizes: ['XS', 'S', 'M', 'L', 'XL'],
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
    // 选项文案走 i18n:放 computed 而非 data,确保 locale 切换时 label 跟随刷新
    sortOptions() {
      return [
        { value: 'default', label: this.$t('goodsList.sortDefault') },
        { value: 'popularity', label: this.$t('goodsList.sortPopularity') },
        { value: 'price_asc', label: this.$t('goodsList.sortPriceAsc') },
        { value: 'price_desc', label: this.$t('goodsList.sortPriceDesc') },
        { value: 'date', label: this.$t('goodsList.sortNewest') },
      ]
    },
    petTypes() {
      return [
        { value: 'dog', label: this.$t('goodsList.petDog') },
        { value: 'cat', label: this.$t('goodsList.petCat') },
        { value: 'other', label: this.$t('goodsList.petOther') },
      ]
    },
    priceRanges() {
      return [
        { value: '0-20', label: this.$t('goodsList.priceUnder20') },
        { value: '20-50', label: this.$t('goodsList.price20to50') },
        { value: '50-100', label: this.$t('goodsList.price50to100') },
        { value: '100+', label: this.$t('goodsList.priceOver100') },
      ]
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
        const mapped = list.map((p) => {
          const basePrice = parseFloat(p.price) || 0
          const campaignPrice = p.campaignPrice != null ? parseFloat(p.campaignPrice) : null
          const hasCampaignPrice = campaignPrice != null && campaignPrice < basePrice
          return {
            id: p.id,
            name: p.name,
            image: p.mainImage || p.images?.[0]?.src || '',
            // 活动价低于原价时优先展示活动价，并把原价作为划线价
            price: hasCampaignPrice ? campaignPrice : basePrice,
            regularPrice: hasCampaignPrice
              ? basePrice
              : parseFloat(p.regularPrice || p.regular_price) || 0,
            campaignTag: p.campaignTag || '',
            ip: p.brandIpId ? this.resolveIpName(p.brandIpId) : null,
          }
        })
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
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.filter-title {
  flex-shrink: 0;
  padding: 32rpx 24rpx 0;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 24rpx;
}

/* 筛选条件独立滚动，footer 不参与滚动，避免小屏被裁切 */
.filter-body {
  flex: 1;
  min-height: 0;
  height: 0;
  padding: 0 24rpx;
  box-sizing: border-box;
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
  flex-shrink: 0;
  display: flex;
  gap: 16rpx;
  padding: 24rpx;
  /* 适配 iPhone 底部安全区，避免按钮被 Home Indicator 遮挡 */
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
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

/* 图片容器：padding-top 百分比相对卡片宽度，高度始终等于宽度（1:1）。
   uni-image 自带 240px 固定高度，直接写 aspect-ratio 在宽屏下不生效，会导致图片被横向裁切 */
.product-image-wrap {
  position: relative;
  width: 100%;
  padding-top: 100%;
  border-radius: var(--radius-sm);
  background: var(--color-background);
}

.product-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
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

/* 营销活动标签 */
.campaign-tag {
  display: inline-block;
  margin-top: 8rpx;
  padding: 2rpx 10rpx;
  font-size: 20rpx;
  color: #e5484d;
  background: rgba(229, 72, 77, 0.1);
  border-radius: 6rpx;
}

.loading,
.empty {
  text-align: center;
  padding: 32rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
