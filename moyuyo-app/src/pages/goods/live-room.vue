<template>
  <view class="live-room">
    <!-- 全屏直播画面占位区 -->
    <view class="live-video-area">
      <!-- 左上角：主播信息 -->
      <view class="anchor-info">
        <view class="anchor-avatar" />
        <view class="anchor-meta">
          <text class="anchor-name">MOYUYO官方</text>
          <view class="viewer-count">
            <text class="viewer-icon" />
            <text class="viewer-num">3.2k</text>
          </view>
        </view>
      </view>
      <!-- 右上角：关闭按钮 -->
      <view class="top-right">
        <view class="overlay-icon-btn" @tap="goBack">
          <text class="luc luc-x" />
        </view>
      </view>
      <!-- 弹幕区域 -->
      <view class="danmaku-container">
        <view class="danmaku-item">小明：这个洗护套装真不错，已下单</view>
        <view class="danmaku-item">猫奴日记：胸背带有货吗？想要黑色的</view>
        <view class="danmaku-item">豆豆妈：今天还有什么优惠</view>
        <view class="danmaku-item">宠物达人小王：主播能介绍下那个外套吗</view>
        <view class="danmaku-item">新手铲屎官：新用户有专享价吗</view>
      </view>
      <!-- 左下角：当前商品讲解标签 -->
      <view class="bottom-left">
        <view class="product-tag-live">
          <text class="luc luc-tag" />
          <text>正在讲解：高端宠物洗护套装</text>
        </view>
      </view>
      <!-- 右下角：分享按钮 -->
      <view class="bottom-right">
        <view class="overlay-icon-btn" @tap="handleShare">
          <text class="luc luc-share-2" />
        </view>
      </view>
    </view>

    <!-- 直播信息栏 -->
    <view class="live-info-bar">
      <view class="live-status-tag live">
        <view class="pulse-dot" />
        <text>直播中</text>
      </view>
      <view class="info-stat">
        <text class="iconfont icon-heart stat-heart" />
        <text>1.8w</text>
      </view>
      <view class="info-stat">
        <text class="iconfont icon-user stat-user" />
        <text>5.6w 粉丝</text>
      </view>
    </view>

    <!-- 商品讲解列表 -->
    <view class="product-panel">
      <view class="drag-indicator" />
      <view class="panel-header">
        <text class="panel-title">直播商品</text>
        <text class="panel-count">共 5 件</text>
      </view>
      <scroll-view scroll-y class="product-scroll">
        <view v-for="(item, i) in productList" :key="i" class="live-product-item">
          <view class="product-thumb" />
          <view class="product-info">
            <view class="product-name-row">
              <text class="product-name">{{ item.name }}</text>
              <text class="explain-status" :class="item.statusClass">
                {{ item.statusText }}
              </text>
            </view>
            <view class="product-price-row">
              <text class="product-price">${{ item.price }}</text>
              <text class="product-original">${{ item.originalPrice }}</text>
              <text class="product-live-tag">直播专享</text>
            </view>
          </view>
          <view class="buy-btn-live" @tap="handleBuy(item)">去购买</view>
        </view>
      </scroll-view>
    </view>

    <!-- 底部互动栏 -->
    <view class="live-interaction-bar">
      <input
        class="live-input"
        type="text"
        placeholder="说点什么..."
        :value="inputValue"
        @input="onInput"
      >
      <view class="interaction-btn send-btn" @tap="handleSend">
        <text class="luc luc-send" />
      </view>
      <view class="interaction-btn" @tap="handleLike">
        <text class="luc luc-heart" />
      </view>
      <view class="interaction-btn" @tap="handleCart">
        <text class="luc luc-shopping-bag" />
        <text class="cart-badge">3</text>
      </view>
    </view>
  </view>
</template>

<script>
import { liveRoomApi } from '@/api'

export default {
  data() {
    return {
      roomId: null,
      inputValue: '',
      roomDetail: null,
      productList: [],
    }
  },

  onLoad(options) {
    this.roomId = options.id
    this.loadRoomDetail()
    this.loadRoomProducts()
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    async loadRoomDetail() {
      try {
        const res = await liveRoomApi.getLiveRoomDetail(this.roomId)
        this.roomDetail = res.data
      } catch (err) {
        uni.showToast({ title: '加载直播间信息失败', icon: 'none' })
      }
    },

    async loadRoomProducts() {
      try {
        const res = await liveRoomApi.getLiveRoomProducts(this.roomId)
        this.productList = res.data || []
      } catch (err) {
        console.error('加载直播商品失败', err)
      }
    },

    handleShare() {
      uni.showToast({ title: '分享功能开发中', icon: 'none' })
    },

    handleBuy(item) {
      uni.showToast({ title: `购买 ${item.name}`, icon: 'none' })
    },

    onInput(e) {
      this.inputValue = e.detail.value
    },

    handleSend() {
      if (!this.inputValue.trim()) return
      uni.showToast({ title: '评论发送成功', icon: 'none' })
      this.inputValue = ''
    },

    handleLike() {
      uni.showToast({ title: '点赞 +1', icon: 'none' })
    },

    handleCart() {
      uni.showToast({ title: '查看购物袋', icon: 'none' })
    },
  },
}
</script>

<style scoped>
.live-room {
  position: relative;
  width: 100%;
  min-height: 100vh;
  background: var(--background-800);
  display: flex;
  flex-direction: column;
}
.live-video-area {
  position: relative;
  width: 100%;
  height: 50vh;
  min-height: 640rpx;
  background: linear-gradient(
    160deg,
    var(--background-800) 0%,
    var(--background-700) 40%,
    var(--background-800) 70%,
    var(--background-900) 100%
  );
  overflow: hidden;
}
.anchor-info {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.anchor-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-300), var(--brand-500));
  border: 4rpx solid rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
}
.anchor-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.anchor-name {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-50);
}
.viewer-count {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 4rpx 16rpx;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.4);
  color: var(--text-50);
  font-size: 22rpx;
}
.viewer-icon {
  width: 24rpx;
  height: 24rpx;
  background: var(--text-50);
  border-radius: 50%;
}
.top-right {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  z-index: 10;
}
.overlay-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  color: var(--text-50);
}
.danmaku-container {
  position: absolute;
  bottom: 140rpx;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column-reverse;
  gap: 12rpx;
  padding: 0 24rpx;
  pointer-events: none;
}
.danmaku-item {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: var(--text-50);
  font-size: 24rpx;
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  align-self: flex-start;
}
.bottom-left {
  position: absolute;
  bottom: 24rpx;
  left: 24rpx;
  z-index: 10;
}
.product-tag-live {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 24rpx;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.5);
  color: var(--text-50);
  font-size: 24rpx;
  font-weight: 500;
  white-space: nowrap;
}
.bottom-right {
  position: absolute;
  bottom: 24rpx;
  right: 24rpx;
  z-index: 10;
}
.live-info-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--background-50);
  border-bottom: 2rpx solid var(--border);
}
.live-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 6rpx 16rpx;
  border-radius: 999px;
  font-size: 22rpx;
  font-weight: 600;
}
.live-status-tag.live {
  background: var(--state-error);
  color: var(--state-error-foreground);
}
.pulse-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--state-error-foreground);
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.7);
  }
}
.info-stat {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  font-size: 24rpx;
  color: var(--text-500);
}
.stat-heart {
  color: var(--state-error);
}
.stat-user {
  color: var(--brand-500);
}
.product-panel {
  flex: 1;
  background: var(--background-50);
  border-top-left-radius: var(--radius);
  border-top-right-radius: var(--radius);
}
.drag-indicator {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999px;
  background: var(--background-400);
  margin: 16rpx auto 8rpx;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx 16rpx;
}
.panel-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
}
.panel-count {
  font-size: 24rpx;
  color: var(--text-400);
}
.product-scroll {
  max-height: 560rpx;
}
.live-product-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  border-bottom: 2rpx solid var(--border);
}
.product-thumb {
  width: 112rpx;
  height: 112rpx;
  border-radius: calc(var(--radius) - 8rpx);
  background: linear-gradient(135deg, var(--background-200), var(--background-300));
  border: 2rpx solid var(--border);
  flex-shrink: 0;
}
.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.product-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.product-name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.explain-status {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.explain-status.explaining {
  background: var(--state-error);
  color: var(--state-error-foreground);
}
.explain-status.explained {
  background: var(--muted);
  color: var(--muted-foreground);
}
.explain-status.pending {
  background: var(--background-200);
  color: var(--text-500);
}
.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}
.product-price {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--state-error);
}
.product-original {
  font-size: 24rpx;
  color: var(--text-300);
  text-decoration: line-through;
}
.product-live-tag {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--state-error);
}
.buy-btn-live {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10rpx 28rpx;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 24rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.live-interaction-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: var(--background-50);
  border-top: 2rpx solid var(--border);
}
.live-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border: 2rpx solid var(--input);
  border-radius: 999px;
  background: var(--background-200);
  color: var(--foreground);
  font-size: 26rpx;
  outline: none;
}
.interaction-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--background-200);
  color: var(--text-600);
  flex-shrink: 0;
  position: relative;
}
.send-btn {
  color: var(--primary);
}
.cart-badge {
  position: absolute;
  top: -4rpx;
  right: -4rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  border-radius: 999px;
  background: var(--state-error);
  color: var(--state-error-foreground);
  font-size: 18rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
</style>
