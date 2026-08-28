<template>
  <view class="product-subscribe">
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">订阅此商品</text>
      <view class="header-spacer" />
    </view>

    <scroll-view class="content" scroll-y>
      <view v-if="product.id" class="product-card">
        <image :src="resolveImage(product)" class="product-image" mode="aspectFill" />
        <view class="product-info">
          <text class="product-name">{{ product.name }}</text>
          <text class="product-desc">{{ stripHtml(product.shortDetail || product.detail) }}</text>
          <view class="product-price-row">
            <text class="product-price">${{ (product.price || 0).toFixed(2) }}</text>
            <text v-if="product.originalPrice" class="product-original-price">
              ${{ product.originalPrice.toFixed(2) }}
            </text>
          </view>
          <view class="sub-save-tag">
            <text class="sub-save-emoji"><text class="luc luc-sparkles" /></text>
            <text class="sub-save-text">订阅享会员价 8 折起</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">商品信息加载中...</view>

      <!-- 周期 -->
      <view class="spec-section">
        <text class="section-title">配送周期</text>
        <view class="spec-options">
          <view
            v-for="p in plans"
            :key="p.id"
            class="spec-option"
            :class="{ active: planId === p.id }"
            @click="planId = p.id"
          >
            <text class="spec-name">{{ p.name }}</text>
            <text class="spec-price">${{ p.price }}/期</text>
          </view>
        </view>
      </view>

      <!-- 数量 -->
      <view class="qty-section">
        <text class="section-title">每次配送数量</text>
        <view class="qty-control">
          <view class="qty-btn" @click="onQty(-1)">-</view>
          <text class="qty-num">{{ quantity }}</text>
          <view class="qty-btn" @click="onQty(1)">+</view>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary subscribe-btn" @click="onSubscribe">
        立即订阅 ${{ (currentPlanPrice * quantity).toFixed(2) }}
      </view>
    </view>
  </view>
</template>

<script>
import { productApi, subscribeApi } from '@/api'

export default {
  data() {
    return {
      productId: null,
      product: {},
      plans: [],
      planId: null,
      quantity: 1,
    }
  },
  computed: {
    currentPlanPrice() {
      const p = this.plans.find((x) => x.id === this.planId)
      return p ? p.price : 0
    },
  },
  onLoad(query) {
    this.productId = query.id
    this.loadData()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    resolveImage(p) {
      if (!p) return ''
      const raw =
        p.mainImage ||
        (Array.isArray(p.images) && p.images[0] && (p.images[0].url || p.images[0].src)) ||
        ''
      if (!raw) return ''
      if (raw.startsWith('http')) return raw
      return raw
    },
    stripHtml(s) {
      if (!s) return ''
      return String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .trim()
        .slice(0, 80)
    },
    async loadData() {
      if (!this.productId) return
      try {
        // 加载商品详情
        const pr = await productApi.getProductDetail(this.productId)
        this.product = (pr && pr.data) || pr || {}

        // 加载订阅方案
        const sr = await subscribeApi.getSubscribePlans()
        const list = (sr && sr.data) || sr || []
        this.plans =
          Array.isArray(list) && list.length
            ? list
            : [
                { id: 'week', name: '每周', price: 9.9 },
                { id: 'month', name: '每月', price: 29.9 },
                { id: 'quart', name: '每季度', price: 79.9 },
              ]
        this.planId = this.plans[0]?.id || null
      } catch (e) {
        console.warn('[product-subscribe] load failed', e)
      }
    },
    onQty(delta) {
      const n = this.quantity + delta
      this.quantity = Math.max(1, Math.min(99, n))
    },
    async onSubscribe() {
      if (!this.planId) {
        uni.showToast({ title: '请选择配送周期', icon: 'none' })
        return
      }
      uni.showLoading({ title: '订阅中...' })
      try {
        await subscribeApi.subscribe(this.planId)
        uni.hideLoading()
        uni.showToast({ title: '订阅成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '订阅失败', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.product-subscribe {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 140rpx;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}
.back-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  font-size: 44rpx;
  color: var(--color-text);
}
.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}
.header-spacer {
  width: 64rpx;
}
.content {
  padding: 24rpx;
}
.product-card {
  background: var(--color-surface);
  border-radius: 24rpx;
  padding: 16rpx;
  display: flex;
  gap: 16rpx;
}
.product-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 16rpx;
  background: var(--color-background);
  flex-shrink: 0;
}
.product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
}
.product-desc {
  font-size: 24rpx;
  color: var(--color-text-secondary);
}
.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}
.product-price {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-primary);
}
.product-original-price {
  font-size: 22rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}
.sub-save-tag {
  background: rgba(219, 201, 138, 0.15);
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.sub-save-emoji {
  font-size: 24rpx;
}
.sub-save-text {
  font-size: 22rpx;
  color: var(--color-primary-dark);
}
.empty {
  text-align: center;
  padding: 48rpx;
  color: var(--color-text-tertiary);
}
.spec-section,
.qty-section {
  background: var(--color-surface);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-top: 16rpx;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 16rpx;
}
.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.spec-option {
  padding: 16rpx 24rpx;
  border: 2rpx solid var(--color-border);
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.spec-option.active {
  border-color: var(--color-primary);
  background: rgba(219, 201, 138, 0.15);
}
.spec-name {
  font-size: 26rpx;
  color: var(--color-text);
}
.spec-price {
  font-size: 22rpx;
  color: var(--color-text-secondary);
}
.qty-control {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.qty-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
}
.qty-num {
  min-width: 80rpx;
  text-align: center;
  font-size: 32rpx;
}
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}
.subscribe-btn {
  height: 88rpx;
  font-size: 30rpx;
  font-weight: 600;
}
.bottom-spacer {
  height: 40rpx;
}
</style>
