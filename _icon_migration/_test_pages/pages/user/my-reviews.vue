<template>
  <view class="my-reviews">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-left" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">我的评价</text>
      <view class="header-right" />
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 统计概览 -->
      <view class="stats-section">
        <view class="stats-card">
          <view class="stat-item">
            <text class="stat-value primary">{{ stats.total }}</text>
            <text class="stat-label">累计评价</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item">
            <text class="stat-value primary">{{ stats.likes }}</text>
            <text class="stat-label">获赞</text>
          </view>
          <view class="stat-divider" />
          <view class="stat-item">
            <text class="stat-value success">{{ stats.highQuality }}</text>
            <text class="stat-label">优质评价</text>
          </view>
        </view>
      </view>

      <!-- Tab 切换栏 -->
      <view class="tabs-section">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: activeTab === tab.key }"
          :style="{
            background: activeTab === tab.key ? 'var(--background)' : 'transparent',
            color: activeTab === tab.key ? 'var(--foreground)' : 'var(--text-400)',
            boxShadow: activeTab === tab.key ? 'var(--shadow-sm)' : 'none',
          }"
          @tap="activeTab = tab.key"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>

      <!-- 评价卡片列表 -->
      <view v-if="filteredReviews.length > 0" class="review-list">
        <view v-for="review in filteredReviews" :key="review.id" class="review-card">
          <view class="review-product-row">
            <view class="review-product-image" />
            <view class="review-product-info">
              <text class="review-product-name">{{ review.productName }}</text>
              <text class="review-product-spec">{{ review.spec }}</text>
              <view class="review-stars">
                <text
                  v-for="i in 5"
                  :key="i"
                  class="star"
                  :class="{ filled: i <= review.rating }">
                  <text class="luc luc-star"></text>
                </text>
                <text class="rating-text">{{ review.rating }}.0</text>
              </view>
            </view>
          </view>

          <text class="review-content">{{ review.content }}</text>

          <view v-if="review.images.length > 0" class="review-images">
            <view v-for="(img, idx) in review.images" :key="idx" class="review-image-placeholder" />
          </view>

          <view v-if="review.shopReply" class="shop-reply">
            <text class="shop-reply-label">店铺回复：</text>
            <text class="shop-reply-text">{{ review.shopReply }}</text>
          </view>

          <view class="review-footer">
            <view class="review-footer-left">
              <text class="review-time">{{ review.time }}</text>
              <view
                v-for="tag in review.tags"
                :key="tag"
                class="review-tag"
                :style="{
                  background: tag === '已回复' ? 'var(--state-success-surface)' : 'var(--brand-50)',
                  color: tag === '已回复' ? 'var(--success)' : 'var(--primary)',
                }"
              >
                <text>{{ tag }}</text>
              </view>
            </view>
            <view class="review-actions">
              <text class="review-action" @tap="handleEditReview(review)">编辑</text>
              <text class="review-action" @tap="handleDeleteReview(review)">删除</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-icon"><text class="luc luc-inbox"></text></text>
        <text class="empty-title">暂无{{ activeTabLabel }}评价</text>
        <text class="empty-desc">完成订单后即可发表评价，您的反馈对其他宠物主人很有帮助</text>
        <view class="go-shop-btn" @tap="handleGoShop">
          <text>去逛逛</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { reviewApi } from '@/api'

export default {
  data() {
    return {
      activeTab: 'all',
      tabs: [
        { key: 'all', label: '全部' },
        { key: 'withImage', label: '有图' },
        { key: 'good', label: '好评' },
        { key: 'bad', label: '差评' },
      ],
      stats: {
        total: 0,
        likes: 0,
        highQuality: 0,
      },
      reviews: [],
    }
  },
  computed: {
    activeTabLabel() {
      const tab = this.tabs.find((t) => t.key === this.activeTab)
      return tab ? tab.label : ''
    },
    filteredReviews() {
      if (this.activeTab === 'all') return this.reviews
      if (this.activeTab === 'withImage') return this.reviews.filter((r) => r.images.length > 0)
      if (this.activeTab === 'good') return this.reviews.filter((r) => r.rating >= 4)
      if (this.activeTab === 'bad') return this.reviews.filter((r) => r.rating <= 2)
      return this.reviews
    },
  },
  onLoad() {
    this.loadReviews()
  },
  methods: {
    async loadReviews() {
      try {
        const res = await reviewApi.getMyReviews()
        const data = res.data || res
        if (data.stats) {
          this.stats = data.stats
        }
        const list = data.items || data || []
        this.reviews = list.map((r) => ({
          id: r.id,
          productName: r.productName || '',
          spec: r.spec || '',
          rating: r.rating || 5,
          content: r.content || '',
          images: r.images || [],
          time: r.time || '',
          tags: r.tags || [],
          shopReply: r.shopReply || null,
        }))
      } catch (e) {
        this.reviews = []
      }
    },
    goBack() {
      uni.navigateBack()
    },
    handleEditReview(review) {
      uni.showToast({ title: '编辑评价', icon: 'none' })
    },
    handleDeleteReview(review) {
      uni.showModal({
        title: '提示',
        content: '确定要删除该评价吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              await reviewApi.deleteReview(review.id)
              const idx = this.reviews.findIndex((r) => r.id === review.id)
              if (idx > -1) this.reviews.splice(idx, 1)
              uni.showToast({ title: '评价已删除', icon: 'success' })
            } catch (e) {
              uni.showToast({ title: '删除失败，请重试', icon: 'none' })
            }
          }
        },
      })
    },
    handleGoShop() {
      uni.switchTab({ url: '/pages/tabbar/home' })
    },
  },
}
</script>

<style scoped lang="scss">
.my-reviews {
  min-height: 100vh;
  background: var(--background);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 30rpx;
  background: var(--background);
  border-bottom: 2rpx solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.header-left,
.header-right {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon {
  font-size: 40rpx;
  color: var(--primary);
}
.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--foreground);
}
.scroll {
  padding-bottom: 40rpx;
}
.stats-section {
  padding: 30rpx;
}
.stats-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  border-radius: 24rpx;
  padding: 28rpx 30rpx;
  background: var(--secondary);
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  flex: 1;
}
.stat-value {
  font-size: 36rpx;
  font-weight: 700;
}
.stat-value.primary {
  color: var(--primary);
}
.stat-value.success {
  color: var(--success);
}
.stat-label {
  font-size: 22rpx;
  color: var(--text-500);
}
.stat-divider {
  width: 2rpx;
  height: 64rpx;
  background: var(--background-300);
}
.tabs-section {
  margin: 0 30rpx;
  display: flex;
  border-radius: 24rpx;
  overflow: hidden;
  padding: 8rpx;
  background: var(--secondary);
}
.tab-item {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 500;
  border-radius: 20rpx;
}
.review-list {
  padding: 24rpx 30rpx;
}
.review-card {
  background: var(--card);
  border: 2rpx solid var(--border);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: var(--shadow-xs);
}
.review-product-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.review-product-image {
  width: 112rpx;
  height: 112rpx;
  border-radius: 16rpx;
  background: var(--background-200);
  flex-shrink: 0;
}
.review-product-info {
  flex: 1;
  min-width: 0;
}
.review-product-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-product-spec {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-stars {
  display: flex;
  align-items: center;
  gap: 4rpx;
  margin-top: 8rpx;
}
.star {
  font-size: 28rpx;
  color: var(--background-400);
}
.star.filled {
  color: var(--brand-300);
}
.rating-text {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--brand-300);
  margin-left: 8rpx;
}
.review-content {
  font-size: 28rpx;
  color: var(--text-600);
  display: block;
  margin-top: 20rpx;
  line-height: 1.6;
}
.review-images {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}
.review-image-placeholder {
  width: 128rpx;
  height: 128rpx;
  border-radius: 16rpx;
  background: var(--background-200);
  flex-shrink: 0;
}
.shop-reply {
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: var(--background-100);
}
.shop-reply-label {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-500);
}
.shop-reply-text {
  font-size: 24rpx;
  color: var(--text-600);
  margin-top: 8rpx;
  display: block;
}
.review-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid var(--border);
}
.review-footer-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}
.review-time {
  font-size: 24rpx;
  color: var(--text-400);
}
.review-tag {
  font-size: 20rpx;
  font-weight: 500;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
}
.review-actions {
  display: flex;
  gap: 16rpx;
}
.review-action {
  font-size: 24rpx;
  color: var(--primary);
  font-weight: 500;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 60rpx 0;
}
.empty-icon {
  font-size: 128rpx;
  color: var(--background-400);
}
.empty-title {
  font-size: 32rpx;
  font-weight: 500;
  color: var(--text-500);
  margin-top: 24rpx;
}
.empty-desc {
  font-size: 28rpx;
  color: var(--text-400);
  text-align: center;
  line-height: 1.6;
  margin-top: 16rpx;
}
.go-shop-btn {
  margin-top: 40rpx;
  height: 80rpx;
  padding: 0 48rpx;
  border-radius: 999rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 28rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
