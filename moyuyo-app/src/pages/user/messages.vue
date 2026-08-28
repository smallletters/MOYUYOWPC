<template>
  <view class="messages">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="back-btn" @click="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left" /></text>
      </view>
      <text class="header-title">消息详情</text>
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
          <text class="type-icon-text"><text class="luc luc-package" /></text>
        </view>
        <text
          class="type-badge"
          :style="{ background: 'var(--color-primary-light)', color: 'var(--color-primary)' }"
        >
          {{ message.typeLabel }}
        </text>
      </view>

      <!-- 消息标题 -->
      <text class="msg-title">{{ message.title }}</text>

      <!-- 消息时间 -->
      <text class="msg-time">{{ message.time }}</text>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 消息正文 -->
      <view class="msg-body">
        <text class="msg-text">{{ message.content }}</text>

        <!-- 扩展信息卡片 -->
        <view v-if="message.info" class="info-card">
          <text class="info-card-title">{{ message.info.title }}</text>
          <view v-for="row in message.info.rows" :key="row.label" class="info-row">
            <text class="info-label">{{ row.label }}</text>
            <text class="info-value">{{ row.value }}</text>
          </view>
          <view v-if="message.info.action" class="info-action">
            <text class="info-action-text" @click="onInfoAction">{{ message.info.action }}</text>
          </view>
        </view>

        <!-- 商品信息卡片 -->
        <view v-if="message.product" class="product-card">
          <text class="info-card-title">商品信息</text>
          <view class="product-row">
            <image class="product-image" :src="message.product.image" mode="aspectFill" />
            <view class="product-detail">
              <text class="product-name">{{ message.product.name }}</text>
              <text class="product-qty">x{{ message.product.quantity }}</text>
            </view>
            <text class="product-price">{{ message.product.price }}</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <button class="action-btn btn-primary" @click="onViewOrder">查看订单</button>
      <button class="action-btn btn-text" @click="onDeleteMessage">删除消息</button>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      isStarred: false,
      message: {
        type: 'order',
        typeLabel: '订单通知',
        title: '您的订单已发货',
        time: '2026-07-08 10:30',
        content: '亲爱的用户，您的订单 #MOY2026070812 已发货。',
        info: {
          title: '物流信息',
          rows: [
            { label: '承运商', value: 'FedEx' },
            { label: '运单号', value: '794644790132' },
            { label: '预计送达', value: '2026-07-12' },
          ],
          action: '查看物流',
        },
        product: {
          image: 'https://via.placeholder.com/128',
          name: '经典尼龙宠物牵引套装',
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
        title: this.isStarred ? '已标星' : '取消标星',
        icon: 'none',
      })
    },

    onViewOrder() {
      uni.showToast({ title: '查看订单', icon: 'none' })
    },

    onDeleteMessage() {
      uni.showModal({
        title: '提示',
        content: '确定删除此消息？',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: '已删除', icon: 'success' })
            uni.navigateBack()
          }
        },
      })
    },

    onInfoAction() {
      uni.showToast({ title: '查看物流', icon: 'none' })
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
