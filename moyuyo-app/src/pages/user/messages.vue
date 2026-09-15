<template>
  <view class="messages">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon luc-arrow-left" />
      </view>
      <text class="header-title">{{ $t('messages.headerTitle') }}</text>
      <view class="header-star" @click="onToggleStar">
        <text class="star-icon" :class="{ starred: isStarred }">
          <text class="luc luc-star" />
        </text>
      </view>
    </view>

    <!-- 消息内容区 -->
    <scroll-view class="content" scroll-y>
      <!-- 消息类型标签 -->
      <view class="type-row">
        <view class="type-icon">
          <text class="type-icon-text luc-package" />
        </view>
        <text
          class="type-badge"
          :style="{ background: 'var(--color-primary-light)', color: 'var(--color-primary)' }"
        >
          {{ $t(message.typeLabelKey) }}
        </text>
      </view>

      <!-- 消息标题 -->
      <text class="msg-title">{{ $t(message.titleKey) }}</text>

      <!-- 消息时间 -->
      <text class="msg-time">{{ message.time }}</text>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 消息正文 -->
      <view class="msg-body">
        <text class="msg-text">{{ $t(message.contentKey) }}</text>

        <!-- 扩展信息卡片 -->
        <view v-if="message.info" class="info-card">
          <text class="info-card-title">{{ $t(message.info.titleKey) }}</text>
          <view v-for="row in message.info.rows" :key="row.labelKey" class="info-row">
            <text class="info-label">{{ $t(row.labelKey) }}</text>
            <text class="info-value">{{ row.value }}</text>
          </view>
          <view v-if="message.info.actionKey" class="info-action">
            <text class="info-action-text" @click="onInfoAction">
              {{ $t(message.info.actionKey) }}
            </text>
          </view>
        </view>

        <!-- 商品信息卡片 -->
        <view v-if="message.product" class="product-card">
          <text class="info-card-title">{{ $t('messages.productInfo') }}</text>
          <view class="product-row">
            <image class="product-image" :src="message.product.image" mode="aspectFill" />
            <view class="product-detail">
              <text class="product-name">{{ $t(message.product.nameKey) }}</text>
              <text class="product-qty">x{{ message.product.quantity }}</text>
            </view>
            <text class="product-price">{{ message.product.price }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <button class="action-btn btn-primary" @click="onViewOrder">
        {{ $t('messages.viewOrder') }}
      </button>
      <button class="action-btn btn-text" @click="onDeleteMessage">
        {{ $t('messages.deleteMessage') }}
      </button>
    </view>
  </view>
</template>

<script>
import { i18n } from '@/i18n'

export default {
  pageTitleKey: 'pageTitle.userMessages',

  data() {
    return {
      isStarred: false,
      message: {
        type: 'order',
        typeLabelKey: 'messages.demoTypeLabel',
        titleKey: 'messages.demoTitle',
        time: '2026-07-08 10:30',
        contentKey: 'messages.demoContent',
        info: {
          titleKey: 'messages.logisticsTitle',
          rows: [
            { labelKey: 'messages.carrier', value: 'FedEx' },
            { labelKey: 'messages.trackingNo', value: '794644790132' },
            { labelKey: 'messages.estimatedDelivery', value: '2026-07-12' },
          ],
          actionKey: 'messages.viewLogistics',
        },
        product: {
          image: 'https://via.placeholder.com/128',
          nameKey: 'messages.demoProductName',
          quantity: 1,
          price: '$89.00',
        },
      },
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onToggleStar() {
      this.isStarred = !this.isStarred
      uni.showToast({
        title: this.isStarred ? i18n.t('messages.starred') : i18n.t('messages.unstarred'),
        icon: 'none',
      })
    },

    onViewOrder() {
      uni.showToast({ title: i18n.t('messages.viewOrder'), icon: 'none' })
    },

    onDeleteMessage() {
      uni.showModal({
        title: i18n.t('messages.noticeTitle'),
        content: i18n.t('messages.deleteConfirm'),
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: i18n.t('messages.deleted'), icon: 'success' })
            uni.navigateBack()
          }
        },
      })
    },

    onInfoAction() {
      uni.showToast({ title: i18n.t('messages.viewLogistics'), icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.messages {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: calc(env(safe-area-inset-bottom) + 160rpx);
}

.header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back-btn {
  position: absolute;
  left: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.back-icon {
  font-size: 48rpx;
  color: var(--color-primary);
  line-height: 1;
}

.header-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.header-star {
  position: absolute;
  right: 16rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-sm);
}

.star-icon {
  font-size: 40rpx;
  color: var(--color-text-tertiary);
  line-height: 1;
}

.star-icon.starred {
  color: var(--color-warm);
}

.content {
  padding: 32rpx;
}

.type-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.type-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-md);
  background: var(--color-divider);
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-icon-text {
  font-size: 32rpx;
  line-height: 1;
}

.type-badge {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: var(--font-weight-semibold);
}

.msg-title {
  display: block;
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  line-height: 1.4;
}

.msg-time {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

.divider {
  margin: 32rpx 0;
  height: 1rpx;
  background: var(--color-divider);
}

.msg-body {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.msg-text {
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.info-card {
  padding: 32rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.info-card-title {
  display: block;
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 24rpx;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.info-label {
  font-size: 26rpx;
  color: var(--color-text-tertiary);
}

.info-value {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.info-action {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.info-action-text {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-primary);
}

.product-card {
  padding: 32rpx;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
}

.product-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.product-image {
  width: 128rpx;
  height: 128rpx;
  border-radius: var(--radius-sm);
  border: 1rpx solid var(--color-divider);
  flex-shrink: 0;
}

.product-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.product-name {
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-qty {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
}

.product-price {
  font-size: 28rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  flex-shrink: 0;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 30;
  padding: 24rpx 32rpx calc(env(safe-area-inset-bottom) + 24rpx);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1rpx solid var(--color-divider);
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.action-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 48rpx;
  font-size: 30rpx;
  font-weight: var(--font-weight-semibold);
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
}

.action-btn.btn-primary {
  background: var(--color-primary);
  color: #fff;
}

.action-btn.btn-text {
  background: transparent;
  color: var(--color-text-tertiary);
  height: auto;
  padding: 8rpx 0;
}

.action-btn::after {
  border: none;
}
</style>
