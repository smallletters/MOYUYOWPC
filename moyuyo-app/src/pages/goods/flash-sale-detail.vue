<template>
  <view class="flash-sale-page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-btn" @click="goBack">
        <text class="nav-back">‹</text>
      </view>
      <text class="nav-title">限时抢购</text>
      <view class="nav-btn" />
    </view>

    <!-- 限时抢购状态横幅 -->
    <view class="flash-header">
      <view class="flash-banner">
        <text class="flash-icon">⚡</text>
        <text class="flash-label">{{ sale.name || '限时抢购进行中' }}</text>
        <view class="countdown">
          <view class="countdown-block">
            <text class="countdown-num">{{ pad(hours) }}</text>
          </view>
          <text class="countdown-sep">:</text>
          <view class="countdown-block">
            <text class="countdown-num">{{ pad(minutes) }}</text>
          </view>
          <text class="countdown-sep">:</text>
          <view class="countdown-block">
            <text class="countdown-num">{{ pad(seconds) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 商品图片轮播 -->
    <swiper
      class="product-swiper"
      :indicator-dots="true"
      :autoplay="false"
      indicator-color="rgba(0,0,0,0.2)"
      indicator-active-color="var(--color-primary)"
    >
      <swiper-item v-for="(img, index) in productImages" :key="index">
        <image :src="img" class="swiper-img" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <!-- 商品信息 -->
    <view class="product-info">
      <view class="price-row">
        <view class="sale-price">
          <text class="price-unit">¥</text>
          <text class="price-value">{{ Math.floor(product.price || 0) }}</text>
          <text class="price-decimal">.{{ Math.round(((product.price || 0) % 1) * 100) }}</text>
        </view>
        <view class="original-price">
          <text class="original-value">¥{{ (product.originalPrice || 0).toFixed(2) }}</text>
          <view v-if="product.originalPrice" class="discount-badge">
            <text class="discount-text">{{ discountLabel }}折</text>
          </view>
        </view>
      </view>
      <text class="product-name">{{ product.name }}</text>
      <text class="product-desc">{{ product.description || '限时特价，售完即止' }}</text>
      <view class="stock-row">
        <text class="stock-label">已抢 {{ sale.soldCount || 0 }} 件</text>
        <text class="stock-label">剩余 {{ sale.stock || 0 }} 件</text>
      </view>
    </view>

    <view class="bottom-spacer" />

    <!-- 底部购买栏 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary buy-btn" :class="{ disabled: sale.stock <= 0 }" @click="onBuy">
        立即抢购 ¥{{ (product.price || 0).toFixed(2) }}
      </view>
    </view>
  </view>
</template>

<script>
import { flashSaleApi, productApi } from '@/api'

export default {
  data() {
    return {
      saleId: null,
      sale: {},
      product: {},
      productImages: [],
      hours: '00',
      minutes: '00',
      seconds: '00',
      timer: null,
    }
  },
  computed: {
    discountLabel() {
      if (!this.product.price || !this.product.originalPrice) return '—'
      return ((this.product.price / this.product.originalPrice) * 10).toFixed(1)
    },
  },
  onLoad(query) {
    this.saleId = query.id
    this.loadData()
  },
  onUnload() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    pad(n) {
      return String(n).padStart(2, '0')
    },
    async loadData() {
      if (!this.saleId) return
      try {
        // 1. 拉限时秒杀详情
        const saleRes = await flashSaleApi.getFlashSaleDetail(this.saleId)
        this.sale = (saleRes && saleRes.data) || saleRes || {}

        // 2. 拉关联商品详情
        if (this.sale.productId) {
          const productRes = await productApi.getProductDetail(this.sale.productId)
          this.product = (productRes && productRes.data) || productRes || {}
        }

        // 3. 商品图
        const imgs = this.product.images || []
        if (Array.isArray(imgs) && imgs.length) {
          this.productImages = imgs
            .map((i) => (typeof i === 'string' ? i : i.url || i.src))
            .filter(Boolean)
        }
        if (this.productImages.length === 0 && this.product.mainImage) {
          this.productImages = [this.product.mainImage]
        }
        if (this.productImages.length === 0) {
          this.productImages = ['https://picsum.photos/600/600?random=80']
        }

        // 4. 倒计时
        this.startCountdown()
      } catch (e) {
        console.warn('[flash-sale-detail] load failed', e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      }
    },
    startCountdown() {
      // 优先用后端返回的 endTime，否则用 sale.durationHours
      const end = this.sale.endTime ? new Date(this.sale.endTime).getTime() : null
      if (!end) {
        this.hours = this.minutes = this.seconds = '--'
        return
      }
      if (this.timer) clearInterval(this.timer)
      this.timer = setInterval(() => {
        const left = Math.max(0, end - Date.now())
        if (left === 0) {
          this.hours = this.minutes = this.seconds = '00'
          clearInterval(this.timer)
          return
        }
        const h = Math.floor(left / 3600000)
        const m = Math.floor((left % 3600000) / 60000)
        const s = Math.floor((left % 60000) / 1000)
        this.hours = h
        this.minutes = m
        this.seconds = s
      }, 1000)
    },
    async onBuy() {
      if (!this.sale || this.sale.stock <= 0) {
        uni.showToast({ title: '已售罄', icon: 'none' })
        return
      }
      uni.showLoading({ title: '抢购中...' })
      try {
        await flashSaleApi.buyFlashSale(this.saleId, 1)
        uni.hideLoading()
        uni.showToast({ title: '抢购成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 800)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: e.message || '抢购失败', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.flash-sale-page {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 140rpx;
}
.nav-bar { display: flex; align-items: center; justify-content: space-between; height: 88rpx; padding: 0 32rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-btn { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; }
.nav-back { font-size: 44rpx; color: var(--color-text); }
.nav-title { font-size: 32rpx; font-weight: 600; color: var(--color-text); }
.flash-header { padding: 24rpx; }
.flash-banner { background: linear-gradient(135deg, #ff4757, #ff6b81); color: #fff; padding: 24rpx 32rpx; border-radius: 24rpx; display: flex; align-items: center; gap: 16rpx; }
.flash-icon { font-size: 36rpx; }
.flash-label { flex: 1; font-size: 28rpx; font-weight: 600; }
.countdown { display: flex; align-items: center; gap: 8rpx; }
.countdown-block { background: rgba(0,0,0,0.4); padding: 4rpx 12rpx; border-radius: 8rpx; }
.countdown-num { font-size: 26rpx; font-weight: 700; color: #fff; }
.countdown-sep { color: #fff; font-weight: 700; }
.product-swiper { width: 100%; height: 750rpx; background: #f2f2f7; }
.swiper-img { width: 100%; height: 100%; }
.product-info { padding: 24rpx; }
.price-row { display: flex; align-items: baseline; gap: 16rpx; }
.sale-price { display: flex; align-items: baseline; color: #ff4757; }
.price-unit { font-size: 24rpx; }
.price-value { font-size: 56rpx; font-weight: 700; }
.price-decimal { font-size: 32rpx; font-weight: 600; }
.original-price { display: flex; align-items: center; gap: 8rpx; }
.original-value { font-size: 24rpx; color: var(--color-text-tertiary); text-decoration: line-through; }
.discount-badge { background: #ff4757; padding: 4rpx 12rpx; border-radius: 4rpx; }
.discount-text { color: #fff; font-size: 22rpx; font-weight: 600; }
.product-name { display: block; margin-top: 16rpx; font-size: 30rpx; font-weight: 600; color: var(--color-text); }
.product-desc { display: block; margin-top: 8rpx; font-size: 26rpx; color: var(--color-text-secondary); }
.stock-row { display: flex; gap: 24rpx; margin-top: 16rpx; }
.stock-label { font-size: 24rpx; color: var(--color-text-tertiary); }
.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: var(--color-surface); border-top: 1rpx solid var(--color-divider); }
.buy-btn { height: 88rpx; font-size: 30rpx; font-weight: 600; }
.buy-btn.disabled { opacity: 0.5; }
.bottom-spacer { height: 40rpx; }
</style>