<template>
  <view class="detail" v-if="product">
    <scroll-view scroll-y class="scroll">
      <!-- 主图/视频轮播 -->
      <swiper class="swiper" indicator-dots autoplay circular>
        <swiper-item v-for="(img, i) in product.images" :key="i">
          <image :src="img.src" class="swiper-image" mode="aspectFill" />
        </swiper-item>
      </swiper>

      <!-- 价格 + 标题 -->
      <view class="header card">
        <view class="price-row">
          <text class="price-large">${{ product.price }}</text>
          <text v-if="product.regular_price && product.regular_price > product.price" class="price-original-large">${{ product.regular_price }}</text>
          <view v-if="product.on_sale" class="sale-tag">SALE</view>
        </view>
        <view class="title-row">
          <text class="title">{{ product.name }}</text>
          <view v-if="ip" class="ip-tag" :class="`tag-${ip.toLowerCase()}`">{{ ip }}</view>
        </view>
        <text class="subtitle">{{ product.short_description?.replace(/<[^>]+>/g, '') }}</text>
      </view>

      <!-- 规格选择 -->
      <view class="section card" @click="skuVisible = true">
        <text class="section-label">Select Variant</text>
        <view class="sku-summary">
          <text v-if="selectedAttrs.length">{{ selectedAttrs.map(a => a.value).join(' / ') }}</text>
          <text v-else class="placeholder">Choose Size / Color</text>
          <text class="arrow">›</text>
        </view>
        <text v-if="stockState" class="stock-state">{{ stockState }}</text>
      </view>

      <!-- 配送 -->
      <view class="section card">
        <view class="ship-row">
          <text class="ship-label">Shipping to</text>
          <text class="ship-value">United States</text>
          <text class="arrow">›</text>
        </view>
        <text class="ship-estimate">Estimated delivery: 3-5 work days</text>
      </view>

      <!-- 商品详情（图文） -->
      <view class="section card">
        <text class="section-title">Product Details</text>
        <view class="content-html" v-html="product.description"></view>
      </view>

      <!-- 评价 -->
      <view class="section card">
        <view class="reviews-header">
          <text class="section-title">Reviews ({{ reviews.length }})</text>
          <text class="arrow" @click="seeAllReviews">See All ›</text>
        </view>
        <view v-if="reviews.length === 0" class="no-reviews">No reviews yet</view>
        <view v-for="r in reviews.slice(0, 2)" :key="r.id" class="review-item">
          <view class="review-head">
            <text class="reviewer">{{ r.reviewer || 'Anonymous' }}</text>
            <text class="review-rating">★ {{ r.rating }}</text>
          </view>
          <text class="review-content">{{ r.review }}</text>
        </view>
      </view>

      <view class="bottom-spacer"></view>
    </scroll-view>

    <!-- SKU 选择弹窗 -->
    <u-popup v-model:show="skuVisible" mode="bottom" border-radius="24">
      <view class="sku-popup">
        <view class="sku-header">
          <image :src="product.images?.[0]?.src" class="sku-image" />
          <view class="sku-info">
            <text class="sku-price">${{ product.price }}</text>
            <text class="sku-stock">Stock: {{ stock }}</text>
          </view>
          <u-icon name="close" size="20" @click="skuVisible = false"></u-icon>
        </view>
        <scroll-view scroll-y class="sku-body">
          <view v-for="attr in attributes" :key="attr.name" class="attr-group">
            <text class="attr-name">{{ attr.name }}</text>
            <view class="attr-options">
              <view
                v-for="opt in attr.options"
                :key="opt"
                class="attr-chip"
                :class="{ active: isAttrSelected(attr.name, opt) }"
                @click="onAttrSelect(attr.name, opt)"
              >{{ opt }}</view>
            </view>
          </view>
        </scroll-view>
        <view class="sku-footer">
          <view class="quantity">
            <text>Quantity</text>
            <view class="quantity-control">
              <view class="qty-btn" @click="onQtyChange(-1)">-</view>
              <text class="qty-value">{{ quantity }}</text>
              <view class="qty-btn" @click="onQtyChange(1)">+</view>
            </view>
          </view>
          <view class="btn btn-primary sku-confirm" @click="onSkuConfirm">Confirm</view>
        </view>
      </view>
    </u-popup>

    <!-- 底部操作栏 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="bar-icon" @click="goCart">
        <text class="bar-emoji">🛒</text>
        <text class="bar-label">Cart</text>
        <view v-if="cartStore.totalQuantity > 0" class="bar-badge">{{ cartStore.totalQuantity }}</view>
      </view>
      <view class="bar-icon" @click="onWishToggle">
        <text class="bar-emoji">{{ wishlisted ? '❤️' : '🤍' }}</text>
        <text class="bar-label">Wishlist</text>
      </view>
      <view class="bar-icon" @click="onService">
        <text class="bar-emoji">💬</text>
        <text class="bar-label">Service</text>
      </view>
      <view class="bar-btn add-cart" @click="onAddCart">Add to Cart</view>
      <view class="bar-btn buy-now" @click="onBuyNow">Buy Now</view>
    </view>
  </view>
  <view v-else class="loading-state">Loading...</view>
</template>

<script>
import { productApi } from '@/api'
import { useCartStore } from '@/store'

export default {
  data() {
    return {
      productId: null,
      product: null,
      reviews: [],
      attributes: [],       // [{name, options: []}]
      selectedAttrs: [],    // [{name, value}]
      quantity: 1,
      stock: 0,
      skuVisible: false,
      wishlisted: false
    }
  },

  computed: {
    cartStore() {
      return useCartStore()
    },
    ip() {
      const tags = this.product?.tags?.map((t) => t.name.toUpperCase()) || []
      return ['MILO', 'LUNA', 'ATLAS', 'OLIVE'].find((t) => tags.includes(t)) || null
    },
    stockState() {
      if (!this.selectedAttrs.length) return ''
      if (this.stock === 0) return 'Out of stock'
      if (this.stock <= 5) return `Only ${this.stock} left`
      return ''
    }
  },

  onLoad(query) {
    this.productId = query.id
    this.loadDetail()
  },

  methods: {
    async loadDetail() {
      try {
        const data = await productApi.getProductDetail(this.productId)
        this.product = data
        this.attributes = data.attributes || []
        this.reviews = await productApi.getProductReviews(this.productId).catch(() => [])
        // 默认库存
        this.stock = data.stock_quantity || 99
      } catch (e) {
        console.error('[detail] load error', e)
        uni.showToast({ title: 'Load failed', icon: 'none' })
      }
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
      // 模拟查询变体库存（实际应调 /products/{id}/variations/{variation_id}）
      this.stock = Math.floor(Math.random() * 50) + 1
    },

    onQtyChange(delta) {
      const next = this.quantity + delta
      if (next >= 1 && next <= this.stock) {
        this.quantity = next
      }
    },

    onSkuConfirm() {
      if (this.attributes.length && this.selectedAttrs.length !== this.attributes.length) {
        uni.showToast({ title: '请选择完整规格', icon: 'none' })
        return
      }
      this.skuVisible = false
    },

    onAddCart() {
      if (!this.product) return
      this.cartStore.addItem({
        productId: this.product.id,
        variationId: this.product.id,
        name: this.product.name,
        image: this.product.images?.[0]?.src,
        price: parseFloat(this.product.price) || 0,
        quantity: this.quantity,
        attrs: this.selectedAttrs
      })
      uni.showToast({ title: 'Added to cart', icon: 'success' })
    },

    onBuyNow() {
      this.onAddCart()
      setTimeout(() => uni.navigateTo({ url: '/pages/cart/index' }), 500)
    },

    onWishToggle() {
      this.wishlisted = !this.wishlisted
      uni.showToast({ title: this.wishlisted ? 'Wishlisted' : 'Removed', icon: 'none' })
    },

    onService() {
      uni.showToast({ title: '客服功能开发中', icon: 'none' })
    },

    goCart() {
      uni.navigateTo({ url: '/pages/cart/index' })
    },

    seeAllReviews() {
      uni.navigateTo({ url: `/pages/order/reviews?productId=${this.productId}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.detail {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.scroll {
  flex: 1;
}

.swiper {
  width: 100%;
  height: 750rpx;
  background: var(--color-surface);
}

.swiper-image {
  width: 100%;
  height: 100%;
}

.header, .section {
  background: var(--color-surface);
  margin: 16rpx;
  padding: 24rpx;
  border-radius: var(--radius-md);
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.price-large {
  font-size: 48rpx;
  font-weight: var(--font-weight-bold);
  color: var(--color-primary-dark);
}

.price-original-large {
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}

.sale-tag {
  padding: 4rpx 12rpx;
  background: var(--color-warm);
  color: #fff;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
}

.title-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin: 16rpx 0 8rpx;
}

.title {
  flex: 1;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: 1.4;
}

.ip-tag {
  padding: 4rpx 12rpx;
  font-size: 20rpx;
  border-radius: var(--radius-pill);
  font-weight: var(--font-weight-medium);
}

.subtitle {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.section-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  display: block;
  margin-bottom: 12rpx;
}

.sku-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-base);
}

.sku-summary .placeholder {
  color: var(--color-text-tertiary);
}

.arrow {
  color: var(--color-text-tertiary);
  font-size: 32rpx;
}

.stock-state {
  display: block;
  margin-top: 8rpx;
  font-size: var(--font-size-sm);
  color: var(--color-danger);
}

.ship-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: var(--font-size-base);
}

.ship-label {
  color: var(--color-text-tertiary);
}

.ship-value {
  flex: 1;
}

.ship-estimate {
  display: block;
  margin-top: 12rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.section-title {
  display: block;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}

.content-html {
  font-size: var(--font-size-base);
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.reviews-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.no-reviews {
  text-align: center;
  padding: 32rpx 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.review-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.review-item:last-child {
  border-bottom: none;
}

.review-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8rpx;
  font-size: var(--font-size-sm);
}

.reviewer {
  color: var(--color-text-secondary);
}

.review-rating {
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.review-content {
  font-size: var(--font-size-base);
  line-height: 1.5;
}

.bottom-spacer {
  height: 120rpx;
}

.bottom-bar {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  padding: 16rpx 24rpx;
  border-top: 1rpx solid var(--color-divider);
  gap: 12rpx;
}

.bar-icon {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  min-width: 80rpx;
}

.bar-emoji {
  font-size: 36rpx;
}

.bar-label {
  font-size: 20rpx;
  color: var(--color-text-tertiary);
}

.bar-badge {
  position: absolute;
  top: -4rpx;
  right: 4rpx;
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

.bar-btn {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  border-radius: var(--radius-pill);
  color: #fff;
}

.add-cart {
  background: var(--color-accent);
}

.buy-now {
  background: var(--color-primary);
  color: var(--color-text);
}

.sku-popup {
  background: var(--color-surface);
  border-radius: 24rpx 24rpx 0 0;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.sku-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.sku-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-md);
  background: var(--color-background);
}

.sku-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.sku-price {
  font-size: 32rpx;
  color: var(--color-primary-dark);
  font-weight: var(--font-weight-semibold);
}

.sku-stock {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.sku-body {
  flex: 1;
  max-height: 600rpx;
  padding: 24rpx;
}

.attr-group {
  margin-bottom: 24rpx;
}

.attr-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  margin-bottom: 16rpx;
  display: block;
}

.attr-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.attr-chip {
  padding: 12rpx 24rpx;
  font-size: var(--font-size-sm);
  background: var(--color-background);
  color: var(--color-text-secondary);
  border-radius: var(--radius-pill);
  border: 2rpx solid transparent;
}

.attr-chip.active {
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark);
  border-color: var(--color-primary);
}

.sku-footer {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-top: 1rpx solid var(--color-divider);
}

.quantity {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: var(--font-size-sm);
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-pill);
}

.qty-btn {
  width: 60rpx;
  height: 60rpx;
  text-align: center;
  line-height: 60rpx;
  font-size: 32rpx;
}

.qty-value {
  min-width: 80rpx;
  text-align: center;
}

.sku-confirm {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
}

.loading-state {
  text-align: center;
  padding: 200rpx 0;
  color: var(--color-text-tertiary);
}
</style>
