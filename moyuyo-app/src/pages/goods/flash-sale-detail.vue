<template>
  <view class="flash-sale-page">
    <!-- 导航栏 -->


    <!-- 限时抢购状态横幅：倒计时由 sale.endTime 驱动,文案随状态切换 -->
    <view class="flash-header">
      <view class="flash-banner">
        <text class="flash-icon luc-zap" />
        <text class="flash-label">{{ bannerLabel }}</text>
        <view v-if="showCountdown" class="countdown">
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
      v-if="productImages.length > 0"
      class="product-swiper"
      :indicator-dots="true"
      :autoplay="false"
      indicator-color="rgba(0,0,0,0.2)"
      indicator-active-color="#ff4757"
    >
      <swiper-item v-for="(img, index) in productImages" :key="index">
        <image :src="img" class="swiper-img" mode="aspectFill" />
      </swiper-item>
    </swiper>
    <view v-else class="product-swiper product-swiper-empty">
      <text class="empty-text">暂无商品图</text>
    </view>

    <!-- 商品信息 -->
    <view class="product-info">
      <view class="price-row">
        <view class="sale-price">
          <text class="price-unit">{{ currencySymbol }}</text>
          <text class="price-value">{{ priceInt }}</text>
          <text class="price-decimal">.{{ priceDecimal }}</text>
        </view>
        <view v-if="product.originalPrice" class="original-price">
          <text class="original-value">
            {{ currencySymbol }}{{ Number(product.originalPrice).toFixed(2) }}
          </text>
          <view v-if="discountLabel" class="discount-badge">
            <text class="discount-text">{{ discountLabel }}折</text>
          </view>
        </view>
      </view>
      <text class="product-name">{{ sale.name || product.name || '限时秒杀' }}</text>
      <text class="product-desc">
        {{ product.description || sale.description || '限时特价，售完即止' }}
      </text>
      <view class="stock-row">
        <text class="stock-label">已抢 {{ sale.soldStock || 0 }} 件</text>
        <text class="stock-label">剩余 {{ stockLeft }} 件</text>
        <text v-if="sale.limitPerUser" class="stock-label">
          每人限购 {{ sale.limitPerUser }} 件
        </text>
      </view>
      <view v-if="sessionNote" class="session-note">
        <text>{{ sessionNote }}</text>
      </view>
    </view>

    <view class="bottom-spacer" />

    <!-- 底部购买栏：根据状态显示文案 -->
    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-primary buy-btn" :class="{ disabled: !canBuy }" @click="onBuy">
        {{ buyBtnLabel }}
      </view>
    </view>
  </view>
</template>

<script>
import { flashSaleApi, productApi } from '@/api'

/**
 * 解析 ISO 8601 / "yyyy-MM-dd HH:mm:ss" / Date 为 Date 对象
 */
function parseDate(val) {
  if (!val) return null
  if (val instanceof Date) return val
  const d = new Date(String(val).replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

export default {
  pageTitleKey: 'pageTitle.goodsFlashSaleDetail',

  data() {
    return {
      saleId: null,
      sale: {},
      product: {},
      productImages: [],
      hours: 0,
      minutes: 0,
      seconds: 0,
      timer: null,
    }
  },

  computed: {
    currencySymbol() {
      const env = (typeof process !== 'undefined' && process.env && process.env.VITE_CURRENCY) || ''
      const win =
        (typeof window !== 'undefined' &&
          window.__MOYUYO_CONFIG__ &&
          window.__MOYUYO_CONFIG__.VITE_CURRENCY) ||
        ''
      const cur = env || win || 'CNY'
      return cur === 'USD' ? '$' : '¥'
    },
    // 当前秒级倒计时状态:'ongoing' / 'upcoming' / 'ended' / 'sold_out' / 'inactive'
    sessionStatus() {
      if (!this.sale || !this.sale.id) return 'inactive'
      if (this.sale.active === false) return 'inactive'
      const now = Date.now()
      const start = parseDate(this.sale.startTime)
      const end = parseDate(this.sale.endTime)
      if (start && now < start.getTime()) return 'upcoming'
      if (end && now > end.getTime()) return 'ended'
      const stockLeft = this.stockLeft
      if (stockLeft <= 0) return 'sold_out'
      return 'ongoing'
    },
    showCountdown() {
      return ['ongoing', 'upcoming'].includes(this.sessionStatus)
    },
    bannerLabel() {
      switch (this.sessionStatus) {
        case 'upcoming':
          return '即将开始'
        case 'ended':
          return '活动已结束'
        case 'sold_out':
          return '已售罄'
        case 'inactive':
          return '活动已下架'
        default:
          return this.sale.name || '限时抢购进行中'
      }
    },
    sessionNote() {
      switch (this.sessionStatus) {
        case 'upcoming': {
          const d = parseDate(this.sale.startTime)
          if (!d) return ''
          return `将于 ${this.formatLocal(d)} 开始`
        }
        case 'ended': {
          const d = parseDate(this.sale.endTime)
          if (!d) return ''
          return `${this.formatLocal(d)} 已结束`
        }
        default:
          return ''
      }
    },
    buyBtnLabel() {
      switch (this.sessionStatus) {
        case 'upcoming':
          return '尚未开始'
        case 'ended':
          return '活动已结束'
        case 'sold_out':
          return '已售罄'
        case 'inactive':
          return '已下架'
        default:
          return `立即抢购 ${this.currencySymbol}${Number(this.product.price || this.sale.flashPrice || 0).toFixed(2)}`
      }
    },
    canBuy() {
      return this.sessionStatus === 'ongoing'
    },
    stockLeft() {
      const total = Number(this.sale.totalStock || 0)
      const sold = Number(this.sale.soldStock || 0)
      return Math.max(0, total - sold)
    },
    priceInt() {
      const v = Number(this.product.price || this.sale.flashPrice || 0)
      return Math.floor(v)
    },
    priceDecimal() {
      const v = Number(this.product.price || this.sale.flashPrice || 0)
      const dec = Math.round((v - Math.floor(v)) * 100)
      return String(dec).padStart(2, '0')
    },
    discountLabel() {
      const cur = Number(this.product.price || this.sale.flashPrice || 0)
      const orig = Number(this.product.originalPrice || 0)
      if (!cur || !orig || orig <= 0) return ''
      return ((cur / orig) * 10).toFixed(1)
    },
  },

  onLoad(query) {
    this.saleId = query.id
    this.loadData()
  },

  onUnload() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  methods: {
    pad(n) {
      return String(n).padStart(2, '0')
    },

    formatLocal(d) {
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    async loadData() {
      if (!this.saleId) return
      try {
        // 1. 拉限时秒杀详情
        const saleRes = await flashSaleApi.getFlashSaleDetail(this.saleId)
        // request.js 已解包 data;此处按可能的形状兜底
        this.sale = (saleRes && saleRes.data) || saleRes || {}

        // 2. 拉关联商品详情
        if (this.sale.productId) {
          try {
            const productRes = await productApi.getProductDetail(this.sale.productId)
            this.product = (productRes && productRes.data) || productRes || {}
          } catch (e) {
            // 商品不存在时仍允许展示秒杀信息
            this.product = {}
          }
        }

        // 3. 商品图：优先 product,否则用占位图
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

    /**
     * 倒计时驱动：
     * - 进行中：endTime - now
     * - 即将开始：startTime - now
     * - 其它：清零
     */
    startCountdown() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
      const tick = () => {
        const status = this.sessionStatus
        if (status === 'ongoing') {
          const end = parseDate(this.sale.endTime)
          if (!end) {
            this.hours = this.minutes = this.seconds = 0
            return
          }
          const left = Math.max(0, end.getTime() - Date.now())
          this.hours = Math.floor(left / 3600000)
          this.minutes = Math.floor((left % 3600000) / 60000)
          this.seconds = Math.floor((left % 60000) / 1000)
        } else if (status === 'upcoming') {
          const start = parseDate(this.sale.startTime)
          if (!start) {
            this.hours = this.minutes = this.seconds = 0
            return
          }
          const left = Math.max(0, start.getTime() - Date.now())
          this.hours = Math.floor(left / 3600000)
          this.minutes = Math.floor((left % 3600000) / 60000)
          this.seconds = Math.floor((left % 60000) / 1000)
        } else {
          this.hours = this.minutes = this.seconds = 0
        }
      }
      tick()
      this.timer = setInterval(tick, 1000)
    },

    async onBuy() {
      if (!this.canBuy) {
        uni.showToast({ title: this.buyBtnLabel, icon: 'none' })
        return
      }
      uni.showLoading({ title: '抢购中...' })
      try {
        await flashSaleApi.buyFlashSale(this.saleId, 1)
        uni.hideLoading()
        uni.showToast({ title: '抢购成功', icon: 'success' })
        // 抢购成功后本地累加 soldStock 提升用户体验
        const sold = Number(this.sale.soldStock || 0) + 1
        this.$set(this.sale, 'soldStock', sold)
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
.nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text);
}
.flash-header {
  padding: 24rpx;
}
.flash-banner {
  background: linear-gradient(135deg, #ff4757, #ff6b81);
  color: #fff;
  padding: 24rpx 32rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-wrap: wrap;
}
.flash-icon {
  font-size: 36rpx;
}
.flash-label {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.countdown {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.countdown-block {
  background: rgba(0, 0, 0, 0.4);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.countdown-num {
  font-size: 26rpx;
  font-weight: 700;
  color: #fff;
  font-variant-numeric: tabular-nums;
}
.countdown-sep {
  color: #fff;
  font-weight: 700;
}
.product-swiper {
  width: 100%;
  height: 750rpx;
  background: #f2f2f7;
}
.product-swiper-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
.empty-text {
  font-size: 26rpx;
}
.swiper-img {
  width: 100%;
  height: 100%;
}
.product-info {
  padding: 24rpx;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}
.sale-price {
  display: flex;
  align-items: baseline;
  color: #ff4757;
}
.price-unit {
  font-size: 24rpx;
}
.price-value {
  font-size: 56rpx;
  font-weight: 700;
}
.price-decimal {
  font-size: 32rpx;
  font-weight: 600;
}
.original-price {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.original-value {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  text-decoration: line-through;
}
.discount-badge {
  background: #ff4757;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}
.discount-text {
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
}
.product-name {
  display: block;
  margin-top: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text);
}
.product-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: var(--color-text-secondary);
}
.stock-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  margin-top: 16rpx;
}
.stock-label {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}
.session-note {
  margin-top: 16rpx;
  padding: 12rpx 16rpx;
  background: var(--color-background);
  border-radius: 8rpx;
  font-size: 24rpx;
  color: var(--color-text-secondary);
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
.buy-btn {
  height: 88rpx;
  font-size: 30rpx;
  font-weight: 600;
  background: linear-gradient(135deg, #ff4757, #ff6b81);
  color: #fff;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.buy-btn.disabled {
  opacity: 0.55;
  background: var(--color-divider);
  color: var(--color-text-tertiary);
}
.bottom-spacer {
  height: 40rpx;
}
</style>
