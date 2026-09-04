<template>
  <view class="category">
    <!-- 顶部：一级分类 Tab（来自后端 mo_category.level=1） -->
    <view class="tabs">
      <view
        v-for="cat in topCategories"
        :key="cat.id"
        class="tab-item"
        :class="{ active: activeTopId === cat.id }"
        @click="onTopChange(cat.id)"
      >
        <text>{{ $tCatName(cat.name) }}</text>
        <view v-if="activeTopId === cat.id" class="tab-indicator" />
      </view>
    </view>

    <!-- 主体：左侧二级分类 + 右侧商品瀑布 -->
    <view class="body">
      <!-- 左侧二级分类（含「全部」入口） -->
      <scroll-view scroll-y class="sidebar">
        <view
          class="sidebar-item"
          :class="{ active: activeSubId === 'ALL' }"
          @click="onSubChange('ALL')"
        >
          <text>{{ $t('category.sidebar.all') }}</text>
        </view>
        <view
          v-for="sub in currentSubs"
          :key="sub.id"
          class="sidebar-item"
          :class="{ active: activeSubId === sub.id }"
          @click="onSubChange(sub.id)"
        >
          <text>{{ $tCatName(sub.name, true) }}</text>
        </view>
        <view v-if="!currentSubs.length" class="sidebar-empty">
          <text>{{ $t('category.empty.sub') }}</text>
        </view>
      </scroll-view>

      <!-- 右侧内容 -->
      <scroll-view scroll-y class="main" @scrolltolower="onLoadMore">
        <!-- 排序 -->
        <view class="sort-bar">
          <view
            v-for="opt in sortOptions"
            :key="opt.value"
            class="sort-item"
            :class="{ active: sortBy === opt.value }"
            @click="onSortChange(opt.value)"
          >
            {{ $t(opt.labelKey) }}
          </view>
        </view>

        <!-- 商品列表（2 列网格） -->
        <view class="product-grid">
          <view
            v-for="p in products"
            :key="p.id"
            class="product-card"
            @click="goDetail(p.id)">
            <image :src="p.image" class="product-image" mode="aspectFill" />
            <view class="product-info">
              <text class="product-name text-ellipsis-2">{{ p.name }}</text>
              <view class="product-bottom">
                <text class="price">${{ p.price }}</text>
                <text v-if="p.ip" class="product-ip" :class="`tag-${p.ip.toLowerCase()}`">
                  {{ p.ip }}
                </text>
              </view>
            </view>
          </view>
          <view v-if="!loading && products.length === 0" class="empty">
            {{ $t('category.empty.product') }}
          </view>
          <view v-if="loading" class="loading">{{ $t('category.loading') }}</view>
          <view
            v-if="!noMore && !loading && products.length > 0"
            class="loadmore"
            @click="onLoadMore"
          >
            {{ $t('category.loadMore') }}
          </view>
          <view v-if="noMore && products.length > 0" class="loadmore done">
            {{ $t('category.noMore') }}
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import { productApi } from '@/api'
import { tCategoryName } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.tabbarCategory',

  data() {
    return {
      // 一级分类（来自后端）
      topCategories: [],
      activeTopId: null,
      // 二级分类（来自当前一级分类的 children）
      currentSubs: [],
      activeSubId: 'ALL',
      // 排序选项
      sortOptions: [
        { value: 'default', labelKey: 'category.sort.default' },
        { value: 'popularity', labelKey: 'category.sort.popularity' },
        { value: 'price_asc', labelKey: 'category.sort.priceAsc' },
        { value: 'price_desc', labelKey: 'category.sort.priceDesc' },
        { value: 'date', labelKey: 'category.sort.date' },
        { value: 'rating', labelKey: 'category.sort.rating' },
      ],
      sortBy: 'default',
      // 商品分页
      products: [],
      loading: false,
      noMore: false,
      page: 1,
      pageSize: 20,
    }
  },

  onLoad() {
    this.loadCategories()
  },

  methods: {
    /**
     * 后端分类名本地化:优先从字典查,查不到回后端原值。
     * isSub=true 用于二级分类(names -> subNames)。
     * 暴露为方法以便 template 里 $tCatName(cat.name) 直接调用,
     * 响应式:i18n.locale 变化时 template 会重渲,自动取最新文本。
     */
    $tCatName(name, isSub) {
      return tCategoryName(name, isSub)
    },

    /** 加载真实分类树 */
    async loadCategories() {
      try {
        const list = await productApi.getCategoryList()
        // 后端返回 level=1 + children 树，过滤掉空一级
        const tops = (Array.isArray(list) ? list : []).filter(
          (c) => Number(c.level) === 1 || !c.parentId || c.parentId === '0' || c.parentId === 0,
        )
        // 让「其他/Uncategorized」一级分类固定排到最右侧，其余保持后端 sort 顺序
        const isOtherCat = (c) =>
          c.name === 'Uncategorized' ||
          this.$tCatName(c.name) === this.$t('category.names.Uncategorized')
        tops.sort((a, b) => Number(isOtherCat(a)) - Number(isOtherCat(b)))
        this.topCategories = tops
        if (tops.length) {
          this.activeTopId = tops[0].id
          this.setCurrentSubs(tops[0])
          this.loadProducts(true)
        } else {
          console.warn('[category] no level-1 categories from backend, list=', list)
        }
      } catch (e) {
        console.error('[category] loadCategories error', e)
        uni.showToast({ title: this.$t('category.loadFailed'), icon: 'none' })
      }
    },

    setCurrentSubs(top) {
      this.currentSubs = Array.isArray(top?.children) ? top.children : []
      this.activeSubId = 'ALL'
    },

    onTopChange(id) {
      if (this.activeTopId === id) return
      this.activeTopId = id
      const top = this.topCategories.find((c) => c.id === id)
      this.setCurrentSubs(top)
      this.loadProducts(true)
    },

    onSubChange(id) {
      if (this.activeSubId === id) return
      this.activeSubId = id
      this.loadProducts(true)
    },

    onSortChange(v) {
      this.sortBy = v
      this.loadProducts(true)
    },

    async loadProducts(reset = false) {
      if (reset) {
        this.page = 1
        this.products = []
        this.noMore = false
      }
      this.loading = true
      try {
        // 排序映射：前端 sortBy → 后端 sortBy/sortOrder
        const sortMap = {
          default: { sortBy: 'createdAt', sortOrder: 'desc' },
          popularity: { sortBy: 'sales', sortOrder: 'desc' },
          price_asc: { sortBy: 'price', sortOrder: 'asc' },
          price_desc: { sortBy: 'price', sortOrder: 'desc' },
          date: { sortBy: 'createdAt', sortOrder: 'desc' },
          rating: { sortBy: 'sales', sortOrder: 'desc' },
        }
        const sort = sortMap[this.sortBy] || sortMap.default
        const params = {
          page: this.page,
          size: this.pageSize,
          sortBy: sort.sortBy,
          sortOrder: sort.sortOrder,
        }
        // 选中具体二级分类时按 categoryId 过滤；'ALL' 或空则按一级父分类
        if (this.activeSubId !== 'ALL' && this.activeSubId != null) {
          params.categoryId = this.activeSubId
        } else if (this.activeTopId) {
          params.parentCategoryId = this.activeTopId
        }
        const pageResult = await productApi.getProductList(params)
        const list =
          pageResult && pageResult.records
            ? pageResult.records
            : Array.isArray(pageResult)
              ? pageResult
              : []
        const mapped = list.map((p) => ({
          id: p.id,
          name: p.name,
          image: this.resolveImage(p),
          price: p.price,
          description: this.stripHtml(p.shortDetail || p.detail || '').slice(0, 80),
          ip: this.detectIP(p),
        }))
        this.products.push(...mapped)
        this.page += 1
        if (list.length < this.pageSize) this.noMore = true
      } catch (e) {
        console.error('[category] loadProducts error', e)
      } finally {
        this.loading = false
      }
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.loadProducts(false)
    },

    /** 主图解析：mainImage 优先，images 兜底 */
    resolveImage(p) {
      if (!p) return ''
      const raw =
        p.mainImage ||
        (Array.isArray(p.images) && p.images[0] && (p.images[0].src || p.images[0].imageUrl)) ||
        ''
      if (!raw) return ''
      if (raw.startsWith('http')) return raw
      // 相对路径(/uploads/...) APP 端没有 dev server，必须拼上后端 base
      if (raw.startsWith('/')) {
        const base = process.env.VITE_ADMIN_API_BASE
        return base ? `${base}${raw}` : raw
      }
      return raw
    },

    /** 去掉 HTML 标签，便于预览描述 */
    stripHtml(s) {
      if (!s) return ''
      return String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .trim()
    },

    detectIP(p) {
      let tags = []
      const raw = p && p.tags
      if (Array.isArray(raw)) {
        tags = raw
          .map((t) => {
            if (typeof t === 'string') return t.toUpperCase()
            if (t && typeof t === 'object') return (t.name || '').toString().toUpperCase()
            return ''
          })
          .filter(Boolean)
      } else if (typeof raw === 'string' && raw.trim()) {
        try {
          const parsed = JSON.parse(raw)
          if (Array.isArray(parsed)) {
            tags = parsed
              .map((t) =>
                (typeof t === 'string' ? t : (t && t.name) || '').toString().toUpperCase(),
              )
              .filter(Boolean)
          }
        } catch (_) {
          tags = raw
            .split(/[,;\s]+/)
            .map((s) => s.toUpperCase())
            .filter(Boolean)
        }
      }
      return ['MILO', 'LUNA', 'ATLAS', 'OLIVE'].find((ip) => tags.includes(ip)) || null
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },
  },
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
  padding: 0 12rpx;
  /* 状态栏安全区：与 home.vue .navbar 保持一致，避免一级分类 Tab 文字被状态栏遮挡 */
  padding-top: calc(env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  border-bottom: 1rpx solid var(--color-divider);
  overflow-x: auto;
  white-space: nowrap;
}
.tab-item {
  position: relative;
  padding: 28rpx 20rpx;
  text-align: center;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  flex-shrink: 0;
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
  width: 180rpx;
  background: var(--color-surface);
}
.sidebar-item {
  padding: 24rpx 12rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  position: relative;
  line-height: 1.3;
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
.sidebar-empty {
  padding: 24rpx 12rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
.main {
  flex: 1;
  padding: 0 16rpx;
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
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding-bottom: 24rpx;
}
.product-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.product-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  background: var(--color-background);
  display: block;
}
.product-info {
  padding: 12rpx 12rpx 16rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.product-name {
  font-size: var(--font-size-sm);
  color: var(--color-text);
  font-weight: var(--font-weight-medium);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.price {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.product-ip {
  padding: 2rpx 8rpx;
  font-size: 18rpx;
  border-radius: var(--radius-sm);
  border: 1rpx solid var(--color-divider);
  color: var(--color-text-tertiary);
}
.empty,
.loading,
.loadmore {
  text-align: center;
  padding: 32rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  grid-column: 1 / -1;
}
.loadmore.done {
  color: var(--color-text-tertiary);
  opacity: 0.7;
}
</style>
