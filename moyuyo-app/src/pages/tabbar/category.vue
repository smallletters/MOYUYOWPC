<template>
  <view class="category">
    <!-- 顶部：4 大产品线 Tab -->
    <view class="tabs">
      <view
        v-for="tab in productLines"
        :key="tab.code"
        class="tab-item"
        :class="{ active: activeTab === tab.code }"
        @click="onTabChange(tab.code)"
      >
        <text>{{ tab.label }}</text>
        <view v-if="activeTab === tab.code" class="tab-indicator"></view>
      </view>
    </view>

    <!-- 主体：左侧二级分类 + 右侧商品瀑布 -->
    <view class="body">
      <!-- 左侧二级分类 -->
      <scroll-view scroll-y class="sidebar">
        <view
          v-for="sub in currentSubs"
          :key="sub.id"
          class="sidebar-item"
          :class="{ active: activeSub === sub.id }"
          @click="onSubChange(sub.id)"
        >
          <text>{{ sub.name }}</text>
        </view>
      </scroll-view>

      <!-- 右侧内容 -->
      <scroll-view scroll-y class="main" @scrolltolower="onLoadMore">
        <!-- 三级筛选维度 -->
        <view class="filters">
          <view class="filter-row">
            <text class="filter-label">Type</text>
            <view class="filter-options">
              <view
                v-for="opt in petTypes"
                :key="opt.value"
                class="filter-chip"
                :class="{ active: filter.petType === opt.value }"
                @click="filter.petType = opt.value"
              >{{ opt.label }}</view>
            </view>
          </view>
          <view class="filter-row">
            <text class="filter-label">Size</text>
            <view class="filter-options">
              <view
                v-for="opt in sizes"
                :key="opt"
                class="filter-chip"
                :class="{ active: filter.size === opt }"
                @click="filter.size = opt"
              >{{ opt }}</view>
            </view>
          </view>
          <view class="filter-row">
            <text class="filter-label">IP</text>
            <view class="filter-options">
              <view
                v-for="opt in ipOptions"
                :key="opt"
                class="filter-chip"
                :class="{ active: filter.ip === opt }"
                @click="filter.ip = opt"
              >{{ opt }}</view>
            </view>
          </view>
        </view>

        <!-- 排序 -->
        <view class="sort-bar">
          <view
            v-for="opt in sortOptions"
            :key="opt.value"
            class="sort-item"
            :class="{ active: sortBy === opt.value }"
            @click="sortBy = opt.value"
          >{{ opt.label }}</view>
        </view>

        <!-- 商品列表 -->
        <view class="product-list">
          <view
            v-for="p in products"
            :key="p.id"
            class="product-row"
            @click="goDetail(p.id)"
          >
            <image :src="p.image" class="product-image" mode="aspectFill" />
            <view class="product-info">
              <text class="product-name">{{ p.name }}</text>
              <text class="product-desc text-ellipsis-2">{{ p.description }}</text>
              <view class="product-bottom">
                <text class="price">${{ p.price }}</text>
                <text v-if="p.ip" class="product-ip" :class="`tag-${p.ip.toLowerCase()}`">{{ p.ip }}</text>
              </view>
            </view>
          </view>
          <view v-if="!loading && products.length === 0" class="empty">No products</view>
          <view v-if="loading" class="loading">Loading...</view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import { productApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'care',
      activeSub: null,
      productLines: [
        { code: 'care', label: 'CARE 洗护' },
        { code: 'gear', label: 'GEAR 出行' },
        { code: 'play', label: 'PLAY 玩具' },
        { code: 'home', label: 'HOME 家居' }
      ],
      // 二级分类映射
      subMap: {
        care: [
          { id: 1, name: '沐浴露' }, { id: 2, name: '护毛素' },
          { id: 3, name: '干洗喷雾' }, { id: 4, name: '耳部清洁' },
          { id: 5, name: '眼部清洁' }, { id: 6, name: '护爪膏' }
        ],
        gear: [
          { id: 11, name: '牵引绳' }, { id: 12, name: '胸背带' },
          { id: 13, name: '便携水杯' }, { id: 14, name: '出行包' },
          { id: 15, name: '反光装备' }, { id: 16, name: '车载安全' }
        ],
        play: [
          { id: 21, name: '毛绒玩具' }, { id: 22, name: '啃咬玩具' },
          { id: 23, name: '益智玩具' }, { id: 24, name: '训练用品' }
        ],
        home: [
          { id: 31, name: '服饰上衣' }, { id: 32, name: '服饰下装' },
          { id: 33, name: '窝床' }, { id: 34, name: '垫子' },
          { id: 35, name: '餐具' }, { id: 36, name: '围巾配饰' }
        ]
      },
      petTypes: [
        { value: 'dog', label: 'Dog' },
        { value: 'cat', label: 'Cat' },
        { value: 'other', label: 'Other' }
      ],
      sizes: ['XS', 'S', 'M', 'L', 'XL'],
      ipOptions: ['MILO', 'LUNA', 'ATLAS', 'OLIVE', 'Classic'],
      sortOptions: [
        { value: 'default', label: '综合' },
        { value: 'popularity', label: '销量' },
        { value: 'price_asc', label: '价格↑' },
        { value: 'price_desc', label: '价格↓' },
        { value: 'date', label: '新品' },
        { value: 'rating', label: '好评' }
      ],
      sortBy: 'default',
      filter: {
        petType: '',
        size: '',
        ip: ''
      },
      products: [],
      loading: false,
      page: 1
    }
  },

  computed: {
    currentSubs() {
      return this.subMap[this.activeTab] || []
    }
  },

  onLoad() {
    this.loadProducts(true)
  },

  methods: {
    onTabChange(code) {
      this.activeTab = code
      this.activeSub = null
      this.loadProducts(true)
    },

    onSubChange(id) {
      this.activeSub = id
      this.loadProducts(true)
    },

    async loadProducts(reset = false) {
      if (reset) {
        this.page = 1
        this.products = []
      }
      this.loading = true
      try {
        // 映射排序
        const orderbyMap = {
          default: 'menu_order',
          popularity: 'popularity',
          price_asc: 'price',
          price_desc: 'price',
          date: 'date',
          rating: 'rating'
        }
        const order = this.sortBy === 'price_desc' ? 'desc' : 'asc'
        const params = {
          page: this.page,
          per_page: 20,
          orderby: orderbyMap[this.sortBy] || 'menu_order',
          order
        }
        if (this.activeSub) params.category = this.activeSub
        const list = await productApi.getProductList(params)
        const mapped = list.map((p) => ({
          id: p.id,
          name: p.name,
          image: p.images?.[0]?.src || '',
          price: p.price,
          description: p.short_description?.replace(/<[^>]+>/g, '').slice(0, 80) || '',
          ip: this.detectIP(p)
        }))
        this.products.push(...mapped)
        this.page += 1
      } catch (e) {
        console.error('[category] load error', e)
      } finally {
        this.loading = false
      }
    },

    detectIP(p) {
      const tags = p.tags?.map((t) => t.name.toUpperCase()) || []
      return ['MILO', 'LUNA', 'ATLAS', 'OLIVE'].find((ip) => tags.includes(ip)) || null
    },

    onLoadMore() {
      this.loadProducts(false)
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.category {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.tabs {
  display: flex;
  background: var(--color-surface);
  padding: 0 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.tab-item {
  flex: 1;
  position: relative;
  padding: 32rpx 0;
  text-align: center;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.tab-item.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.sidebar {
  width: 200rpx;
  background: var(--color-surface);
}

.sidebar-item {
  padding: 32rpx 16rpx;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  position: relative;
}

.sidebar-item.active {
  background: var(--color-background);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.sidebar-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 6rpx;
  height: 32rpx;
  background: var(--color-primary);
  border-radius: 0 3rpx 3rpx 0;
}

.main {
  flex: 1;
  padding: 0 16rpx;
}

.filters {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16rpx;
  margin: 16rpx 0;
}

.filter-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12rpx;
}

.filter-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  width: 80rpx;
  padding-top: 8rpx;
}

.filter-options {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.filter-chip {
  padding: 8rpx 20rpx;
  font-size: var(--font-size-xs);
  background: var(--color-background);
  color: var(--color-text-secondary);
  border-radius: var(--radius-pill);
}

.filter-chip.active {
  background: var(--color-primary);
  color: var(--color-text);
}

.sort-bar {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
  padding: 16rpx 0;
}

.sort-item {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.sort-item.active {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.product-row {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16rpx;
  gap: 16rpx;
}

.product-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: var(--radius-sm);
  background: var(--color-background);
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.product-name {
  font-size: var(--font-size-base);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
}

.product-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 8rpx 0;
}

.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.product-ip {
  padding: 4rpx 10rpx;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
}

.empty, .loading {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
