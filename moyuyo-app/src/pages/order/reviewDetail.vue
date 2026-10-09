<template>
  <view class="review-detail">
    <view class="page-header">
      <view class="header-back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('orderReviewDetail.title') }}</text>
    </view>

    <!-- 订单信息 -->
    <view v-if="order" class="card order-card">
      <text class="order-no">#{{ order.orderNo }}</text>
      <view class="status-row">
        <text class="status-label">{{ $t('orderReviewDetail.orderStatus') }}</text>
        <text class="status-value">{{ orderStatusText }}</text>
      </view>
    </view>

    <!-- 评价列表 -->
    <scroll-view scroll-y class="scroll">
      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>
      <view v-else-if="reviews.length === 0" class="empty">
        {{ $t('orderReviewDetail.empty') }}
      </view>
      <view v-for="r in reviews" :key="r.id" class="card review-card">
        <view class="product-row">
          <text class="product-name">{{ productName(r.productId) || '-' }}</text>
          <view class="stars">
            <text
              v-for="i in 5"
              :key="i"
              class="star"
              :class="{ filled: (r.rating || 0) >= i }">
              {{ (r.rating || 0) >= i ? '★' : '☆' }}
            </text>
          </view>
        </view>
        <view v-if="r.tags && r.tags.length" class="tags">
          <text v-for="tag in r.tags" :key="tag" class="tag-chip">{{ tag }}</text>
        </view>
        <text v-if="r.content" class="content">{{ r.content }}</text>
        <view v-if="r.images && r.images.length" class="images">
          <image
            v-for="(img, idx) in r.images"
            :key="idx"
            :src="img"
            class="image-preview"
            mode="aspectFill"
            @click="previewImage(r.images, idx)"
          />
        </view>
        <text class="meta">
          {{ $t('orderReviewDetail.reviewedAt', { time: formatDate(r.createTime) }) }}
        </text>
      </view>
      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template>

<script>
import { orderApi, reviewApi } from '@/api'
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.orderReviewDetail',

  data() {
    return {
      orderId: null,
      order: null,
      orderItems: [], // 用于按 productId 查商品名称
      reviews: [],
      loading: true,
      localeVersion: 0,
    }
  },

  computed: {
    orderStatusText() {
      void this.localeVersion
      const key = `orderStatus.${this.order?.status}`
      const v = i18n.t(key)
      return v === key ? this.order?.status || '' : v
    },
  },

  onLoad(query) {
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    this.orderId = query.orderId
    this.load()
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    goBack() {
      uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/order/list' }) })
    },
    async load() {
      if (!this.orderId) {
        this.loading = false
        return
      }
      try {
        // 并行拉订单详情 + 我的评价列表（按 orderId 客户端过滤）
        const [order, myReviews] = await Promise.all([
          orderApi.getOrderDetail(this.orderId),
          reviewApi.getMyReviews({ page: 1, size: 100 }),
        ])
        this.order = order
        this.orderItems = Array.isArray(order?.items) ? order.items : []
        const list = Array.isArray(myReviews?.records) ? myReviews.records : []
        this.reviews = list.filter((r) => Number(r.orderId) === Number(this.orderId))
      } catch (e) {
        console.warn('[reviewDetail] load failed', e)
      } finally {
        this.loading = false
      }
    },
    /** 用订单项里的 productName 拼出来，没有时回退 '-' */
    productName(productId) {
      const it = this.orderItems.find((i) => Number(i.productId) === Number(productId))
      return it ? it.productName : ''
    },
    formatDate(d) {
      if (!d) return ''
      const s = String(d)
      return s.length >= 16 ? s.slice(0, 16).replace('T', ' ') : s
    },
    previewImage(images, idx) {
      uni.previewImage({ urls: images, current: idx })
    },
  },
}
</script>

<style lang="scss" scoped>
.review-detail {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100vh;
  background: var(--color-background);
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface, #fff);
  border-bottom: 1rpx solid var(--color-divider, #eee);
}

.header-back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--color-text-primary, #2e2b29);
  flex: 1;
  text-align: center;
  margin-right: 56rpx;
}

.scroll {
  flex: 1;
  padding: 16rpx;
  box-sizing: border-box;
}

.card {
  width: 100%;
  background: var(--color-surface, #fff);
  border-radius: var(--radius-md, 12rpx);
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-sizing: border-box;
  box-shadow: 0 1rpx 4rpx rgba(0, 0, 0, 0.04);
}

.order-card .order-no {
  font-family: monospace;
  font-size: 26rpx;
  color: var(--color-text-primary, #2e2b29);
}

.status-row {
  display: flex;
  justify-content: space-between;
  margin-top: 12rpx;
  font-size: 24rpx;
}

.status-label {
  color: var(--color-text-secondary, #6e6e73);
}

.status-value {
  color: var(--color-text-primary, #2e2b29);
  font-weight: 600;
}

.review-card .product-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text-primary, #2e2b29);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 12rpx;
}

.stars {
  display: flex;
  gap: 4rpx;
  flex-shrink: 0;
}

.star {
  font-size: 32rpx;
  color: var(--color-divider, #d5d5d5);
}

.star.filled {
  color: var(--color-primary, #f0c14b);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.tag-chip {
  padding: 4rpx 14rpx;
  font-size: 22rpx;
  background: rgba(219, 201, 138, 0.15);
  color: var(--color-primary-dark, #8a7335);
  border-radius: var(--radius-pill, 999rpx);
}

.content {
  display: block;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--color-text-primary, #2e2b29);
  margin-bottom: 12rpx;
  word-break: break-word;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.image-preview {
  width: 160rpx;
  height: 160rpx;
  border-radius: var(--radius-sm, 6rpx);
  background: var(--color-background, #f7f7f7);
}

.meta {
  display: block;
  font-size: 22rpx;
  color: var(--color-text-tertiary, #8e8e93);
  text-align: right;
}

.loading,
.empty {
  padding: 80rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--color-text-tertiary, #8e8e93);
}

.bottom-spacer {
  height: 40rpx;
}
</style>
