<template>
  <view class="share-product">
    <!-- 顶部导航栏 -->
    <view class="header">
      <view class="header-btn" @tap="goBack">
        <text class="back-icon"><text class="luc luc-arrow-left"></text></text>
      </view>
      <text class="header-title">分享商品</text>
      <view class="header-btn" @tap="handleClose">
        <text class="close-icon"><text class="luc luc-x"></text></text>
      </view>
    </view>

    <scroll-view scroll-y class="scroll">
      <!-- 分享预览卡片 -->
      <view class="preview-section">
        <view class="share-card">
          <view class="product-image-area">
            <view class="product-main-image" />
            <view class="brand-watermark">MOYUYO</view>
          </view>
          <view class="product-body">
            <text class="share-product-name">{{ product.name }}</text>
            <text class="share-product-desc">{{ product.desc }}</text>
            <view class="share-price-row">
              <text class="share-price">¥{{ product.price }}</text>
              <text class="share-original-price">¥{{ product.originalPrice }}</text>
            </view>
            <view class="share-divider" />
            <view class="share-qr-row">
              <view class="qr-code">
                <view
                  v-for="(cell, idx) in qrMatrix"
                  :key="idx"
                  class="qr-cell"
                  :class="{ filled: cell === 1, empty: cell === 0 }"
                />
              </view>
              <view class="qr-info">
                <text class="qr-label">扫码查看详情</text>
                <view class="qr-recommender">
                  <view class="recommender-avatar">
                    <text><text class="luc luc-user"></text></text>
                  </view>
                  <text class="recommender-name">推荐人：{{ recommender }}</text>
                </view>
                <text class="qr-source">来自 MOYUYO 宠物商城</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 分享渠道 -->
      <view class="section">
        <text class="section-title">分享到</text>
        <view class="share-channels">
          <view
            v-for="channel in channels"
            :key="channel.id"
            class="channel-item"
            @tap="handleShare(channel)"
          >
            <view class="channel-icon" :style="{ background: channel.bg }">
              <text v-if="channel.brand" class="channel-emoji channel-brand">{{ channel.brand }}</text>
              <text v-else class="channel-emoji luc" :class="$luc(channel.icon)"></text>
            </view>
            <text class="channel-name">{{ channel.name }}</text>
          </view>
        </view>
      </view>

      <!-- 分享设置 -->
      <view class="section">
        <view class="settings-card">
          <view
            v-for="setting in shareSettings"
            :key="setting.id"
            class="setting-row"
            :style="{ borderBottom: !setting.last ? '2rpx solid var(--border)' : 'none' }"
          >
            <text class="setting-label">{{ setting.label }}</text>
            <view
              class="toggle-track"
              :class="{ active: setting.value }"
              @tap="toggleSetting(setting.id)"
            >
              <view
                class="toggle-thumb"
                :style="{ transform: setting.value ? 'translateX(40rpx)' : 'translateX(0)' }"
              />
            </view>
          </view>
        </view>
      </view>

      <!-- 自定义消息 -->
      <view class="section">
        <text class="section-title">添加分享文案（可选）</text>
        <view class="message-input-wrapper">
          <textarea
            v-model="customMessage"
            class="message-input"
            placeholder="写一段推荐语..."
            maxlength="200"
          />
          <text class="char-count">{{ customMessage.length }}/200</text>
        </view>
      </view>

      <!-- 生成分享图片按钮 -->
      <view class="generate-btn" @tap="handleGenerateImage">
        <text><text class="luc luc-arrow-up"></text> 生成分享图片</text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>
  </view>
</template><script>
export default {
  data() {
    return {
      customMessage: '',
      recommender: '宠物爱好者',
      product: {
        name: '高端宠物洗护套装',
        desc: '温和清洁 持久留香',
        price: 189,
        originalPrice: 259,
      },
      channels: [
        { id: 'email', name: 'Email', icon: 'mail', bg: 'var(--background-200)' },
        { id: 'messages', name: 'Messages', icon: 'message-circle', bg: 'var(--state-success-surface)' },
        { id: 'whatsapp', name: 'WhatsApp', icon: 'upload', bg: 'var(--state-success-surface)' },
        { id: 'twitter', name: 'X', icon: 'twitter', brand: '𝕏', bg: 'var(--background-200)' },
        { id: 'copy', name: '复制链接', icon: 'link', bg: 'var(--brand-50)' },
        { id: 'facebook', name: 'Facebook', icon: 'facebook', brand: 'f', bg: 'var(--background-200)' },
      ],
      shareSettings: [
        { id: 'caption', label: '添加分享文案', value: false },
        { id: 'price', label: '显示价格', value: true },
        { id: 'watermark', label: '添加水印', value: true },
      ],
      qrMatrix: this.generateQRMatrix(),
    }
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    handleClose() {
      uni.navigateBack()
    },
    handleShare(channel) {
      uni.showToast({ title: `分享到 ${channel.name}`, icon: 'success' })
    },
    toggleSetting(id) {
      const setting = this.shareSettings.find((s) => s.id === id)
      if (setting) setting.value = !setting.value
    },
    handleGenerateImage() {
      uni.showToast({ title: '分享图片已生成', icon: 'success' })
    },
    generateQRMatrix() {
      const pattern = [
        [1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1],
        [1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 1],
        [1, 0, 1, 1, 1, 0, 1, 0, 0, 1, 0],
        [1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1],
        [1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0],
        [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 1],
        [1, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0],
        [0, 1, 1, 1, 1, 1, 0, 0, 1, 0, 1],
        [1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 1],
        [1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0],
        [1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1],
      ]
      return pattern.flat()
    },
  },
}
</script>

<style scoped lang="scss">
.share-product {
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
.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-icon,
.close-icon {
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
.preview-section {
  display: flex;
  justify-content: center;
  padding: 40rpx 30rpx 30rpx;
}
.share-card {
  width: 640rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--background-50);
  box-shadow: var(--shadow-lg);
}
.product-image-area {
  position: relative;
  overflow: hidden;
}
.product-main-image {
  width: 100%;
  aspect-ratio: 4/3;
  background: linear-gradient(135deg, var(--brand-50), var(--brand-100));
}
.brand-watermark {
  position: absolute;
  bottom: 24rpx;
  right: 24rpx;
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--text-800);
  opacity: 0.12;
  pointer-events: none;
}
.product-body {
  padding: 30rpx;
  border-top: 2rpx solid var(--border);
}
.share-product-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-800);
  display: block;
}
.share-product-desc {
  font-size: 24rpx;
  color: var(--text-400);
  display: block;
  margin-top: 8rpx;
}
.share-price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-top: 16rpx;
}
.share-price {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--primary);
}
.share-original-price {
  font-size: 24rpx;
  text-decoration: line-through;
  color: var(--text-300);
}
.share-divider {
  height: 2rpx;
  background: var(--border);
  margin: 24rpx 0;
}
.share-qr-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.qr-code {
  display: grid;
  grid-template-columns: repeat(11, 1fr);
  gap: 2rpx;
  width: 160rpx;
  height: 160rpx;
  padding: 8rpx;
  border-radius: 8rpx;
  background: var(--background-50);
  flex-shrink: 0;
}
.qr-cell {
  aspect-ratio: 1;
  border-radius: 1px;
}
.qr-cell.filled {
  background: var(--text-800);
}
.qr-cell.empty {
  background: var(--background-50);
}
.qr-info {
  flex: 1;
  min-width: 0;
}
.qr-label {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-600);
  display: block;
}
.qr-recommender {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}
.recommender-avatar {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: var(--background-200);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
}
.recommender-name {
  font-size: 24rpx;
  color: var(--text-400);
}
.qr-source {
  font-size: 20rpx;
  color: var(--text-400);
  display: block;
  margin-top: 8rpx;
}
.section {
  padding: 0 30rpx 30rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--foreground);
  display: block;
  margin-bottom: 20rpx;
}
.share-channels {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
}
.channel-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.channel-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.channel-emoji {
  font-size: 48rpx;
}
.channel-brand {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-900);
}
.channel-name {
  font-size: 22rpx;
  font-weight: 500;
  color: var(--text-500);
}
.settings-card {
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--card);
  border: 2rpx solid var(--border);
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  height: 100rpx;
}
.setting-label {
  font-size: 28rpx;
  color: var(--foreground);
}
.toggle-track {
  position: relative;
  width: 102rpx;
  height: 62rpx;
  border-radius: 999rpx;
  background: var(--background-300);
  transition: background-color 0.2s;
  flex-shrink: 0;
}
.toggle-track.active {
  background: var(--primary);
}
.toggle-thumb {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 50%;
  background: var(--background-50);
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s;
}
.message-input-wrapper {
  position: relative;
}
.message-input {
  width: 100%;
  height: 160rpx;
  border-radius: 24rpx;
  padding: 24rpx;
  background: var(--background-200);
  font-size: 28rpx;
  color: var(--foreground);
  border: none;
  outline: none;
  box-sizing: border-box;
  resize: none;
}
.char-count {
  position: absolute;
  bottom: 16rpx;
  right: 24rpx;
  font-size: 20rpx;
  color: var(--text-400);
}
.generate-btn {
  margin: 0 30rpx;
  height: 104rpx;
  border-radius: 999rpx;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  box-shadow: var(--shadow-sm);
}
.bottom-spacer {
  height: 40rpx;
}
</style>
