<template>
  <view v-if="loading && !product" class="loading-state">加载中...</view>

  <view v-else-if="product" class="detail">
    <!-- 顶部导航栏 -->
    <view class="navbar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="navbar-inner">
        <view class="nav-btn" aria-label="返回" @click="goBack">
          <text class="luc" :class="$luc('chevron-left')" />
        </view>
        <view class="nav-actions">
          <view class="nav-btn" aria-label="分享" @click="onShare">
            <text class="luc" :class="$luc('share-2')" />
          </view>
          <view class="nav-btn" aria-label="收藏" @click="onWishToggle">
            <text class="fav-icon" :class="{ 'is-fav': wishlisted }">
              {{ wishlisted ? '❤' : '♡' }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <scroll-view scroll-y class="scroll" @scrolltolower="onScrollLower">
      <!-- 商品图片轮播 + 缩略图 -->
      <view class="media-section">
        <swiper
          class="swiper"
          :current="currentImageIndex"
          circular
          indicator-dots
          indicator-active-color="var(--brand-500)"
          indicator-color="var(--background-400)"
          @change="onSwiperChange"
        >
          <swiper-item v-for="(img, i) in galleryImages" :key="img.key || i">
            <image
              :src="img.url"
              class="swiper-image"
              mode="aspectFill"
              @tap="previewImage(i)" />
          </swiper-item>
        </swiper>

        <!-- 缩略图行 -->
        <scroll-view v-if="galleryImages.length > 1" scroll-x class="thumbs-row">
          <view
            v-for="(img, i) in galleryImages"
            :key="'thumb-' + (img.key || i)"
            class="thumb"
            :class="{ active: i === currentImageIndex }"
            @tap="onThumbTap(i)"
          >
            <image :src="img.url" mode="aspectFill" class="thumb-img" />
          </view>
        </scroll-view>
      </view>

      <!-- 商品信息区 -->
      <view class="info-section">
        <text class="brand-eyebrow">{{ brandLine }}</text>
        <text class="product-title">{{ product.name }}</text>
        <view class="price-row">
          <text class="price-current">${{ formatPrice(currentPrice) }}</text>
          <text v-if="originalPriceNum && originalPriceNum > currentPrice" class="price-original">
            ${{ formatPrice(originalPriceNum) }}
          </text>
          <view v-if="memberPrice && memberPrice < currentPrice" class="member-tag">
            会员价 ${{ formatPrice(memberPrice) }}
          </view>
        </view>
        <view v-if="product.shortDetail" class="short-desc">{{ product.shortDetail }}</view>
      </view>

      <!-- 规格选择区(行内 chip + 选中摘要,点击打开弹窗) -->
      <view v-if="hasVariations" class="variant-section">
        <view v-for="group in attributeGroups" :key="group.name" class="variant-row">
          <text class="variant-label">{{ group.name }}</text>
          <view class="variant-options">
            <view
              v-for="opt in group.options"
              :key="opt"
              class="variant-chip"
              :class="{ active: isAttrSelected(group.name, opt) }"
              @tap="onAttrSelect(group.name, opt)"
            >
              {{ opt }}
            </view>
          </view>
        </view>
        <view v-if="stockState" class="stock-state">{{ stockState }}</view>
      </view>

      <!-- 分隔条 -->
      <view class="divider-bar" />

      <!-- Tab 导航 -->
      <view class="tabs">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @tap="activeTab = tab.key"
        >
          {{ tab.label }}
        </view>
      </view>

      <!-- 图文详情：仅渲染后端 mo_product.description/detail 字段 -->
      <view v-if="activeTab === 'detail'" class="tab-panel">
        <rich-text v-if="product.detail" class="rich-content" :nodes="product.detail" />
        <view v-else class="empty-detail">暂无详情</view>
      </view>

      <!-- 规格参数 -->
      <view v-else-if="activeTab === 'spec'" class="tab-panel">
        <view v-if="specRows.length" class="spec-list">
          <view v-for="row in specRows" :key="row.label" class="spec-row">
            <text class="spec-label">{{ row.label }}</text>
            <text class="spec-value">{{ row.value }}</text>
          </view>
        </view>
        <view v-else class="empty-detail">{{ $t('goodsDetail.emptySpec') }}</view>
      </view>

      <!-- 用户评价 -->
      <view v-else-if="activeTab === 'review'" class="tab-panel">
        <view class="review-summary">
          <view class="review-score">
            <text class="score-num">{{ reviewRateText }}</text>
            <text class="score-label">好评率</text>
          </view>
          <text class="review-count">· {{ reviewTotalCount }} 条评价</text>
        </view>
        <view v-if="reviewTags.length" class="review-tags">
          <view v-for="tag in reviewTags" :key="tag.label" class="review-tag">
            {{ tag.label }}({{ tag.count }})
          </view>
        </view>

        <view v-if="reviews.length === 0" class="empty-reviews">暂无评价</view>
        <view v-for="r in displayedReviews" :key="r.id" class="review-card">
          <image :src="r.avatar || defaultAvatar" mode="aspectFill" class="review-avatar" />
          <view class="review-body">
            <view class="review-head">
              <text class="reviewer">{{ r.reviewerName || '匿名用户' }}</text>
              <text class="review-date">{{ formatRelativeTime(r.createTime) }}</text>
            </view>
            <view class="rating-stars">
              <text
                v-for="n in 5"
                :key="'s-' + r.id + '-' + n"
                class="luc review-star"
                :class="n <= (r.rating || 0) ? 'filled' : 'empty'"
              >
                ★
              </text>
            </view>
            <text class="review-content">{{ r.content }}</text>
          </view>
        </view>

        <view v-if="reviews.length > 3" class="see-all-btn" @tap="seeAllReviews">
          查看全部评价 ›
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部固定操作栏 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="bar-icon" @tap="goCart">
        <text class="luc" :class="$luc('shopping-cart')" />
        <text class="bar-label">{{ $t('cart.title') || '购物车' }}</text>
        <view v-if="cartStore.totalQuantity > 0" class="bar-badge">
          {{ cartStore.totalQuantity }}
        </view>
      </view>
      <view class="bar-icon" @tap="onWishToggle">
        <text class="fav-icon" :class="{ 'is-fav': wishlisted }">{{ wishlisted ? '❤' : '♡' }}</text>
        <text class="bar-label">
          {{ wishlisted ? $t('goodsDetail.unfavorite') : $t('goodsDetail.favorite') }}
        </text>
      </view>
      <view class="bar-icon" @tap="onService">
        <text class="luc" :class="$luc('message-circle')" />
        <text class="bar-label">{{ $t('goodsDetail.service') }}</text>
      </view>
      <view class="bar-btn cart-btn" @tap="onAddCart">{{ $t('goodsDetail.addCart') }}</view>
      <view class="bar-btn buy-btn" @tap="onBuyNow">{{ $t('goodsDetail.buyNow') }}</view>
    </view>
  </view>

  <view v-else class="loading-state">
    <text>{{ errorMessage || '商品不存在' }}</text>
    <view class="retry-btn" @tap="retryLoad">重新加载</view>
  </view>
</template>

<script>
import { productApi, reviewApi } from '@/api'
import { useCartStore } from '@/store'
import browsingHistory from '@/utils/browsingHistory'
import { i18n } from '@/i18n'

export default {
  data() {
    return {
      statusBarHeight: 20,
      productId: null,
      product: null,
      loading: true,
      errorMessage: '',
      // 图集
      galleryImages: [],
      defaultImages: [], // 后端返回的原始图集(不含变体图),用于切规格时重置图集
      currentImageIndex: 0,
      // 规格变体
      attributeGroups: [], // [{name, options: []}]
      variations: [], // [{id, attrs:[{name,value}], stock, price, image}]
      selectedAttrs: [], // [{name, value}]
      stock: 0,
      // 评价
      reviews: [],
      reviewTotalCount: 0,
      // UI 状态
      activeTab: 'detail',
      tabs: [
        { key: 'detail', label: '图文详情' },
        { key: 'spec', label: '规格参数' },
        { key: 'review', label: '用户评价' },
      ],
      wishlisted: false,
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    },
    // 默认头像兜底:指向静态资源,确保 image 永远有 src
    defaultAvatar() {
      // 使用 base64 1x1 透明图避免空 src 在部分端报 warn
      return 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxIDEiPjxjaXJjbGUgZmlsbD0iI2VlZTllNGQiIGN4PSIwLjUiIGN5PSIwLjUiIHI9IjAuNSIvPjwvc3ZnPg=='
    },
    // 当前展示价：SKU 命中走 SKU 价,否则用商品主价
    currentPrice() {
      return parseFloat(this.product?.price) || 0
    },
    originalPriceNum() {
      return parseFloat(this.product?.originalPrice) || 0
    },
    // 会员价（如后端 attributes JSON 中携带,否则隐藏标签）
    memberPrice() {
      return this.parseMemberPrice()
    },
    brandLine() {
      const map = { 1: 'MOYUYO CARE', 2: 'MOYUYO GEAR', 3: 'MOYUYO PLAY', 4: 'MOYUYO HOME' }
      const ip = this.product?.brandIpId
      return map[ip] || 'MOYUYO'
    },
    hasVariations() {
      return this.attributeGroups.length > 0
    },
    stockState() {
      void this.localeVersion
      if (!this.selectedAttrs.length) return ''
      if (this.stock === 0) return i18n.t('goodsDetail.outOfStock')
      if (this.stock <= 5) return i18n.t('goodsDetail.stockLow', { count: this.stock })
      return ''
    },
    reviewRateText() {
      if (!this.reviewTotalCount) return '100%'
      // 平均评分 5 分制 → 好评率 = 评分>=4 的占比
      const avg = this.averageRating
      return Math.min(100, Math.round((avg / 5) * 100)) + '%'
    },
    averageRating() {
      if (!this.reviews.length) return 5
      const sum = this.reviews.reduce((s, r) => s + (r.rating || 0), 0)
      return sum / this.reviews.length
    },
    reviewTags() {
      // 从评价 tags 聚合 Top 4
      const counter = new Map()
      this.reviews.forEach((r) => {
        if (Array.isArray(r.tags)) {
          r.tags.forEach((t) => {
            if (!t) return
            counter.set(t, (counter.get(t) || 0) + 1)
          })
        }
      })
      return Array.from(counter.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 4)
        .map(([label, count]) => ({ label, count }))
    },
    displayedReviews() {
      return this.reviews.slice(0, 3)
    },
    specRows() {
      if (!this.product) return []
      const rows = []
      const tags = (this.product.tags || '')
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
      if (tags.length) rows.push({ label: '标签', value: tags.join(' / ') })
      if (this.product.brandIpId) rows.push({ label: '产品线', value: this.brandLine })
      if (this.product.weight) rows.push({ label: '重量', value: this.product.weight + ' kg' })
      if (this.product.spuCode) rows.push({ label: '商品编码', value: this.product.spuCode })
      if (this.product.productType)
        rows.push({ label: '商品类型', value: this.product.productType })
      if (this.stock != null) rows.push({ label: '库存', value: String(this.stock) })
      return rows
    },
  },

  onLoad(query) {
    this.productId = query.id
    // 读取系统状态栏高度(自定义导航栏时需要)
    try {
      const sysInfo = uni.getSystemInfoSync()
      this.statusBarHeight = sysInfo.statusBarHeight || 20
    } catch (e) {
      this.statusBarHeight = 20
    }
    this.loadDetail()
  },

  methods: {
    /**
     * 加载商品详情。带 1 次自动重试 + 延长超时
     * 后端 / Spring Boot / DB 任一环节慢都会触发超时
     */
    async loadDetail(retry = 0) {
      this.loading = true
      try {
        const data = await productApi.getProductDetail(this.productId)
        this.product = data
        // 记录本次浏览（仅本地存储，不调用后端）；缺字段时按空字符串兜底
        browsingHistory.recordView({
          id: data.id ?? this.productId,
          name: data.name,
          image:
            data.mainImage ||
            data.image ||
            data.cover ||
            (Array.isArray(data.images) && data.images[0]?.url) ||
            '',
          price: data.price,
        })

        // 1. 组装图集(主图 + images[] + 变体图,去重)
        this.galleryImages = this.buildGallery(data)
        // 保存后端原始图集,切规格时基于它叠变体图(避免旧变体图残留)
        this.defaultImages = [...this.galleryImages]

        // 2. 解析 attributes:custom_attributes + variations
        const parsed = this.parseAttributes(data.attributes)
        this.attributeGroups = parsed.attributeGroups
        this.variations = parsed.variations

        // 3. 默认选中第一个 SKU 的规格(若有)
        const firstSku = data.skus?.[0]
        if (firstSku?.spec) {
          this.selectedAttrs = this.parseSpecString(firstSku.spec)
          this.stock = firstSku.stock ?? 0
        } else if (this.attributeGroups.length === 1) {
          // 单维度变体自动选中第一个值
          this.selectedAttrs = [
            {
              name: this.attributeGroups[0].name,
              value: this.attributeGroups[0].options[0],
            },
          ]
          this.syncStockBySelection()
        } else if (this.attributeGroups.length > 1) {
          // 多维度变体：用第一个变体作为默认选中(若存在)
          const firstVar = this.variations[0]
          if (firstVar) {
            this.selectedAttrs = [...firstVar.attrs]
            this.syncStockBySelection()
          }
        } else {
          // 非变体商品：用商品主库存
          this.stock = firstSku?.stock ?? data.stock ?? 0
        }

        // 4. 查询该商品是否已被当前用户收藏(用于初始化心形状态)
        try {
          const { cartApi } = await import('@/api')
          const favRes = await cartApi.getFavorites()
          const favList = favRes?.data || favRes?.records || favRes?.items || favRes || []
          const records = Array.isArray(favList) ? favList : []
          this.wishlisted = records.some((f) => Number(f.productId) === Number(this.productId))
        } catch (e) {
          // 未登录/未授权时静默失败,保持 wishlisted = false
          console.warn('[detail] favorite status load failed', e)
        }

        // 5. 加载评价(失败不影响主流程)
        try {
          const rev = await reviewApi.getProductReviews(this.productId, {
            page: 1,
            size: 20,
          })
          this.reviews = rev?.records || rev || []
          this.reviewTotalCount =
            rev?.total ?? (Array.isArray(this.reviews) ? this.reviews.length : 0)
        } catch (e) {
          console.warn('[detail] reviews load failed', e)
          this.reviews = []
          this.reviewTotalCount = 0
        }
      } catch (e) {
        console.error('[detail] load error', e)
        // 网络/超时错误自动重试 1 次
        const isNetworkErr = /timeout|network|failed/i.test(e?.message || '')
        if (retry < 1 && isNetworkErr) {
          console.warn('[detail] retry load detail once')
          return this.loadDetail(retry + 1)
        }
        this.errorMessage = e?.message || '加载失败'
        uni.showToast({
          title: e?.message?.includes('timeout')
            ? '连接服务器超时,请检查后端 8080 是否启动'
            : '商品加载失败',
          icon: 'none',
          duration: 2500,
        })
      } finally {
        this.loading = false
      }
    },

    /**
     * 组装详情页图集
     * 顺序：变体图(若当前选中变体有图) → 后端 images[] → 主图 mainImage
     */
    buildGallery(data) {
      const list = []
      const seen = new Set()
      const push = (url, key) => {
        if (!url || seen.has(url)) return
        seen.add(url)
        list.push({ url, key: key || url })
      }
      if (Array.isArray(data.images)) {
        data.images.forEach((img, i) => {
          const url = typeof img === 'string' ? img : img?.url
          push(url, `img:${i}`)
        })
      }
      push(data.mainImage, 'main')
      return list
    },

    /**
     * 解析 mo_product.attributes JSON
     * 形态:{ custom_attributes: [{name, options}], variations: [...] }
     */
    parseAttributes(raw) {
      const result = { attributeGroups: [], variations: [] }
      if (!raw || typeof raw !== 'string') return result
      let obj
      try {
        obj = JSON.parse(raw)
      } catch (e) {
        return result
      }
      const groups = Array.isArray(obj.custom_attributes) ? obj.custom_attributes : []
      for (const g of groups) {
        if (g && g.name && Array.isArray(g.options) && g.options.length) {
          result.attributeGroups.push({
            name: g.name,
            options: g.options.filter((o) => o != null && o !== ''),
          })
        }
      }
      const variations = Array.isArray(obj.variations) ? obj.variations : []
      for (const v of variations) {
        if (!v || !Array.isArray(v.attributes)) continue
        const attrs = v.attributes
          .filter((a) => a && a.name)
          .map((a) => ({ name: a.name, value: a.value }))
        result.variations.push({
          id: v.id,
          attrs,
          stock:
            typeof v.stockQuantity === 'number'
              ? v.stockQuantity
              : typeof v.stock === 'number'
                ? v.stock
                : null,
          price: typeof v.salePrice === 'number' && v.salePrice > 0 ? v.salePrice : v.regularPrice,
          image: v.image?.src || null,
          enabled: v.enabled !== false,
        })
      }
      return result
    },

    /**
     * 将 SKU.spec 字符串(如 "香草白/500ml")解析为 [{name, value}]
     * 找不到对应属性名时,按"按顺序匹配"兜底
     */
    parseSpecString(spec) {
      if (!spec) return []
      const parts = String(spec)
        .split(/[/,\uff0f]/)
        .map((s) => s.trim())
        .filter(Boolean)
      const groups = this.attributeGroups
      if (!groups.length) return parts.map((p) => ({ name: '规格', value: p }))
      return parts.map((p, i) => ({
        name: groups[i]?.name || `规格${i + 1}`,
        value: p,
      }))
    },

    /**
     * 从 attributes JSON 抽取会员价(若有)
     */
    parseMemberPrice() {
      if (!this.product?.attributes) return null
      try {
        const obj = JSON.parse(this.product.attributes)
        const v = obj?.member_price ?? obj?.memberPrice ?? obj?.primePrice
        if (typeof v === 'number' && v > 0 && v < this.currentPrice) return v
      } catch (e) {
        /* ignore */
      }
      return null
    },

    /** 把价格保留两位小数 */
    formatPrice(value) {
      const n = parseFloat(value) || 0
      return n.toFixed(2)
    },

    /** 缩略图/轮播联动 */
    onSwiperChange(e) {
      this.currentImageIndex = e.detail.current
    },
    onThumbTap(i) {
      this.currentImageIndex = i
    },

    /** 评价时间相对显示 */
    formatRelativeTime(dt) {
      if (!dt) return ''
      // 后端返回的是字符串或 LocalDateTime 序列化的 "yyyy-MM-dd HH:mm:ss"
      const d = typeof dt === 'string' ? new Date(dt.replace(/-/g, '/')) : new Date(dt)
      if (isNaN(d.getTime())) return ''
      const diffMs = Date.now() - d.getTime()
      const days = Math.floor(diffMs / 86400000)
      if (days <= 0) return '今天'
      if (days === 1) return '1天前'
      if (days < 7) return `${days}天前`
      if (days < 14) return '1周前'
      if (days < 30) return `${Math.floor(days / 7)}周前`
      return `${Math.floor(days / 30)}个月前`
    },

    isAttrSelected(name, value) {
      return !!this.selectedAttrs.find((a) => a.name === name && a.value === value)
    },

    onAttrSelect(name, value) {
      const exist = this.selectedAttrs.find((a) => a.name === name)
      if (exist) {
        exist.value = value
      } else {
        this.selectedAttrs.push({ name, value })
      }
      this.syncStockBySelection()
    },

    /**
     * 根据当前 selectedAttrs 在 variations 中匹配真实库存/价格/图片
     * - 命中：变体图置顶(主图切到变体图),刷新价格/库存
     * - 未命中：图集回到后端原图,保守置 0
     */
    syncStockBySelection() {
      if (!this.variations.length) return
      const sel = this.selectedAttrs
        .map((a) => `${a.name}:${a.value}`)
        .sort()
        .join('|')
      const match = this.variations.find((v) => {
        const key = v.attrs
          .map((a) => `${a.name}:${a.value}`)
          .sort()
          .join('|')
        return key === sel
      })
      if (!match) {
        // 未命中：清空图集中的变体图,回到后端默认图集
        this.galleryImages = [...this.defaultImages]
        this.currentImageIndex = 0
        this.stock = 0
        return
      }
      if (typeof match.stock === 'number') this.stock = match.stock
      if (typeof match.price === 'number' && match.price > 0) {
        this.product.price = match.price
      }
      // 图集 = [变体图] + 后端原图(去重),变体图始终在第一位
      const baseImages = match.image
        ? [{ url: match.image, key: `var:${match.id}` }, ...this.defaultImages]
        : [...this.defaultImages]
      this.galleryImages = this.dedupeImages(baseImages)
      this.currentImageIndex = 0
      this.product.mainImage = match.image || this.product.mainImage
    },

    /**
     * 图集去重：按 url 去重,保留首次出现的项
     */
    dedupeImages(list) {
      const seen = new Set()
      const out = []
      for (const item of list) {
        if (!item || !item.url || seen.has(item.url)) continue
        seen.add(item.url)
        out.push(item)
      }
      return out
    },

    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.reLaunch({ url: '/pages/tabbar/home' })
      }
    },

    /** 错误页"重新加载"按钮:重置状态并重发请求 */
    retryLoad() {
      this.errorMessage = ''
      this.loading = true
      this.loadDetail(0)
    },

    onUnload() {
      if (this._unsubLocale) this._unsubLocale()
    },

    previewImage(i) {
      if (!this.galleryImages.length) return
      uni.previewImage({
        current: this.galleryImages[i]?.url,
        urls: this.galleryImages.map((g) => g.url),
      })
    },

    goCart() {
      uni.navigateTo({ url: '/pages/cart/index' })
    },

    onShare() {
      // 跳到分享商品页并带上商品 id，便于页面加载真实商品信息与生成对应二维码
      const productId = this.productId || (this.product && this.product.id)
      if (!productId) {
        uni.showToast({ title: '商品信息未就绪', icon: 'none' })
        return
      }
      uni.navigateTo({ url: `/pages/goods/share-product?id=${productId}` })
    },

    /**
     * 收藏/取消收藏:
     * - 乐观更新 UI(立即翻转 wishlisted),失败时回滚
     * - 调用后端 /api/v1/favorites,确保 favorites 页面 getFavorites() 能看到这条记录
     */
    async onWishToggle() {
      if (!this.product) return
      const next = !this.wishlisted
      this.wishlisted = next
      try {
        // 动态引用避免与现有 import 冲突
        const { cartApi } = await import('@/api')
        const skuId = this.product.skus?.[0]?.id || null
        if (next) {
          await cartApi.addFavorite(this.product.id, skuId)
          uni.showToast({ title: '已收藏', icon: 'success' })
        } else {
          await cartApi.removeFavorite(this.product.id, skuId)
          uni.showToast({ title: '已取消收藏', icon: 'none' })
        }
      } catch (e) {
        // 失败回滚
        this.wishlisted = !next
        uni.showToast({ title: e.message || '收藏失败', icon: 'none' })
      }
    },

    /**
     * 跳转到客服中心,带上当前商品信息作为上下文
     * 让客服页能预填"关于该商品"的快捷消息,提升用户体验
     */
    onService() {
      if (!this.product) return
      // 用 encodeURIComponent 防止商品名包含特殊字符破坏 query
      const params = [
        `productId=${encodeURIComponent(this.product.id)}`,
        `productName=${encodeURIComponent(this.product.name || '')}`,
      ]
      uni.navigateTo({
        url: `/pages/user/customer-service?${params.join('&')}`,
      })
    },

    onAddCart() {
      if (!this.product) return
      const firstSku = this.product.skus?.[0]
      this.cartStore.addItem({
        skuId: firstSku?.id || this.product.id,
        productId: this.product.id,
        name: this.product.name,
        image: this.galleryImages[0]?.url || this.product.mainImage,
        price: parseFloat(this.product.price) || 0,
        quantity: 1,
        attrs: this.selectedAttrs,
      })
      uni.showToast({ title: '已加入购物车', icon: 'success' })
    },

    onBuyNow() {
      if (!this.product) return
      const firstSku = this.product.skus?.[0]
      // 立即购买:设置临时单品直接进入结算,不写入购物车
      this.cartStore.setBuyNow({
        skuId: firstSku?.id || this.product.id,
        productId: this.product.id,
        name: this.product.name,
        image: this.galleryImages[0]?.url || this.product.mainImage,
        price: parseFloat(this.product.price) || 0,
        quantity: 1,
        attrs: this.selectedAttrs,
      })
      uni.navigateTo({ url: '/pages/cart/checkout' })
    },

    seeAllReviews() {
      uni.navigateTo({
        url: `/pages/goods/qa?productId=${this.productId}`,
      })
    },

    onScrollLower() {
      // 预留：详情到底加载更多评价
    },
  },
}
</script>

<style lang="scss" scoped>
/* 设计稿 Apple 风格 design tokens → MOYUYO 品牌色 */
.detail {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--background-200, #ede9e4);
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 40;
  background-color: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1rpx solid var(--border, #eae5dd);
}

.navbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 16rpx;
}

.nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 9999rpx;
  border: 1rpx solid var(--border, #eae5dd);
  background-color: var(--color-surface, #ffffff);
  color: var(--color-text, #2e2b29);
  font-size: 28rpx;
}

/* 收藏图标:用 unicode 心形字符 ♡(空心)/ ❤(实心)切换
   不依赖字体字形是否提供填充版本,跨平台一致 */
.fav-icon {
  font-size: 36rpx;
  line-height: 1;
  color: var(--text-500, #7a746c);
  transition:
    color 0.18s ease,
    transform 0.18s ease;
}

.fav-icon.is-fav {
  /* 选中:实心绿,符合"已收藏"的成功语义 */
  color: #34c759;
  /* 轻微缩放反馈 */
  transform: scale(1.08);
}

.nav-actions {
  display: flex;
  gap: 8rpx;
}

.scroll {
  flex: 1;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32rpx;
  text-align: center;
  padding: 200rpx 0;
  color: var(--color-text-tertiary, #9a948c);
  font-size: 28rpx;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  padding: 0 48rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--brand-500, #dbc98a);
  background-color: var(--color-surface, #ffffff);
  border: 1rpx solid var(--brand-500, #dbc98a);
  border-radius: 9999rpx;
}

/* ====== 图片轮播 ====== */
.media-section {
  background-color: var(--color-surface, #ffffff);
}

.swiper {
  width: 100%;
  height: 750rpx;
  padding: 16rpx 16rpx 8rpx;
  box-sizing: border-box;
}

.swiper-image {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-md, 16rpx);
  background-color: var(--background-200, #ede9e4);
}

.thumbs-row {
  white-space: nowrap;
  padding: 16rpx 16rpx 24rpx;
}

.thumb {
  display: inline-block;
  width: 56px;
  height: 56px;
  border-radius: 12rpx;
  overflow: hidden;
  border: 2rpx solid var(--border, #eae5dd);
  margin-right: 8rpx;
  vertical-align: middle;
}

.thumb.active {
  border-color: var(--brand-500, #dbc98a);
}

.thumb-img {
  width: 100%;
  height: 100%;
}

/* ====== 商品信息区 ====== */
.info-section {
  padding: 24rpx 32rpx;
  background-color: var(--color-surface, #ffffff);
}

.brand-eyebrow {
  font-size: 22rpx;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-500, #7a746c);
}

.product-title {
  display: block;
  margin-top: 8rpx;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-800, #2e2b29);
  word-break: break-word;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-top: 16rpx;
}

.price-current {
  font-size: 48rpx;
  font-weight: 600;
  color: var(--brand-500, #dbc98a);
  font-variant-numeric: tabular-nums;
}

.price-original {
  font-size: 26rpx;
  color: var(--text-400, #9a948c);
  text-decoration: line-through;
  font-variant-numeric: tabular-nums;
}

.member-tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  font-size: 22rpx;
  font-weight: 600;
  border-radius: 9999rpx;
  background-color: var(--brand-50, #f8f3e6);
  color: var(--brand-500, #dbc98a);
  white-space: nowrap;
}

.short-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--text-500, #7a746c);
}

/* ====== 规格选择区 ====== */
.variant-section {
  padding: 24rpx 32rpx;
  background-color: var(--color-surface, #ffffff);
  margin-top: 16rpx;
}

.variant-row {
  margin-bottom: 24rpx;
}

.variant-row:last-of-type {
  margin-bottom: 0;
}

.variant-label {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-800, #2e2b29);
  margin-bottom: 16rpx;
}

.variant-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.variant-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 32rpx;
  height: 64rpx;
  font-size: 26rpx;
  font-weight: 500;
  border-radius: 9999rpx;
  background-color: var(--color-surface, #ffffff);
  color: var(--text-800, #2e2b29);
  border: 1rpx solid var(--border, #eae5dd);
  white-space: nowrap;
}

.variant-chip.active {
  background-color: var(--brand-500, #dbc98a);
  color: var(--primary-foreground, #ffffff);
  border-color: var(--brand-500, #dbc98a);
}

.stock-state {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--color-danger, #c96e5f);
}

/* ====== 分隔条 / Tab ====== */
.divider-bar {
  height: 16rpx;
  background-color: var(--background-200, #ede9e4);
}

.tabs {
  display: flex;
  background-color: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--border, #eae5dd);
}

.tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  font-size: 28rpx;
  font-weight: 500;
  white-space: nowrap;
  color: var(--text-400, #9a948c);
  border-bottom: 4rpx solid transparent;
}

.tab.active {
  color: var(--brand-500, #dbc98a);
  font-weight: 600;
  border-bottom-color: var(--brand-500, #dbc98a);
}

.tab-panel {
  background-color: var(--color-surface, #ffffff);
  padding-bottom: 32rpx;
}

.rich-content {
  display: block;
  padding: 24rpx 32rpx;
}

.empty-detail {
  text-align: center;
  padding: 80rpx 0;
  font-size: 26rpx;
  color: var(--text-400, #9a948c);
}

/* ====== 规格参数 ====== */
.spec-list {
  padding: 24rpx 32rpx;
}

.spec-row {
  display: flex;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--border, #eae5dd);
  font-size: 26rpx;
}

.spec-row:last-child {
  border-bottom: none;
}

.spec-label {
  flex: 0 0 160rpx;
  color: var(--text-400, #9a948c);
}

.spec-value {
  flex: 1;
  color: var(--text-800, #2e2b29);
}

/* ====== 用户评价 ====== */
.review-summary {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx 16rpx;
}

.review-score {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.score-num {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--brand-500, #dbc98a);
  font-variant-numeric: tabular-nums;
}

.score-label {
  font-size: 26rpx;
  color: var(--text-500, #7a746c);
}

.review-count {
  font-size: 24rpx;
  color: var(--text-400, #9a948c);
  margin-left: 8rpx;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 0 32rpx 24rpx;
}

.review-tag {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  font-weight: 500;
  border-radius: 9999rpx;
  background-color: var(--background-200, #ede9e4);
  color: var(--text-600, #6e6962);
  white-space: nowrap;
}

.empty-reviews {
  text-align: center;
  padding: 60rpx 0;
  font-size: 26rpx;
  color: var(--text-400, #9a948c);
}

.review-card {
  display: flex;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid var(--border, #eae5dd);
}

.review-card:last-child {
  border-bottom: none;
}

.review-avatar {
  flex-shrink: 0;
  width: 72rpx;
  height: 72rpx;
  border-radius: 9999rpx;
  background-color: var(--background-200, #ede9e4);
}

.review-body {
  flex: 1;
  min-width: 0;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reviewer {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-800, #2e2b29);
}

.review-date {
  font-size: 22rpx;
  color: var(--text-400, #9a948c);
  white-space: nowrap;
}

.rating-stars {
  display: flex;
  gap: 4rpx;
  margin-top: 8rpx;
}

.review-star {
  font-size: 24rpx;
  line-height: 1;
}

.review-star.filled {
  color: var(--brand-500, #dbc98a);
}

.review-star.empty {
  color: var(--background-400, #d1ccc4);
}

.review-content {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--text-600, #6e6962);
}

.see-all-btn {
  margin: 32rpx 32rpx 0;
  padding: 24rpx 0;
  text-align: center;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-500, #7a746c);
  border-top: 1rpx solid var(--border, #eae5dd);
}

/* ====== 底部操作栏 ====== */
.bottom-spacer {
  height: 160rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 120rpx;
  padding: 0 32rpx;
  background-color: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1rpx solid var(--border, #eae5dd);
}

.bar-icon {
  position: relative;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  color: var(--text-500, #7a746c);
  font-size: 36rpx;
}

.bar-label {
  font-size: 20rpx;
  margin-top: 4rpx;
}

.bar-badge {
  position: absolute;
  top: -4rpx;
  right: 8rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  background-color: var(--color-danger, #c96e5f);
  color: #ffffff;
  font-size: 20rpx;
  border-radius: 9999rpx;
  text-align: center;
  line-height: 32rpx;
}

.bar-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80rpx;
  font-size: 26rpx;
  font-weight: 600;
  border-radius: 9999rpx;
}

.cart-btn {
  background-color: var(--color-surface, #ffffff);
  color: var(--brand-500, #dbc98a);
  border: 1rpx solid var(--brand-500, #dbc98a);
}

.buy-btn {
  background-color: var(--brand-500, #dbc98a);
  color: var(--primary-foreground, #ffffff);
}

/* iPhone X 及以上底部安全区 */
.safe-area-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
