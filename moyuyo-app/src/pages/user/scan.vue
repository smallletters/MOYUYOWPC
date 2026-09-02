<template>
  <view class="scan-page">
    <view class="header">
      <view class="header-btn" @click="goBack">
        <text class="header-icon luc-arrow-left" />
      </view>
      <text class="header-title">扫一扫</text>
      <view class="header-btn" @click="onPickAlbum">
        <text class="header-icon luc-image" />
      </view>
    </view>

    <view class="scan-area">
      <view class="scan-frame">
        <view class="scan-line" />
        <view class="corner corner-tl" />
        <view class="corner corner-tr" />
        <view class="corner corner-bl" />
        <view class="corner corner-br" />
      </view>
      <text class="scan-hint">将二维码/条形码放入框内，即可自动扫描</text>
    </view>

    <view class="bottom-bar">
      <view class="bottom-btn" @click="onToggleFlash">
        <view class="btn-circle">
          <text class="btn-icon luc-zap" />
        </view>
        <text class="btn-label">闪光灯</text>
      </view>
      <view class="bottom-btn" @click="onPickAlbum">
        <view class="btn-circle">
          <text class="btn-icon luc-image" />
        </view>
        <text class="btn-label">相册</text>
      </view>
      <view class="bottom-btn" @click="onViewHistory">
        <view class="btn-circle">
          <text class="btn-icon luc-clock" />
        </view>
        <text class="btn-label">记录</text>
      </view>
    </view>

    <view v-if="showResult" class="result-modal">
      <view class="result-overlay" @click="showResult = false" />
      <view class="result-content">
        <text class="result-title">扫描结果</text>
        <text class="result-data">{{ scanResult }}</text>
        <view class="result-actions">
          <view class="result-btn result-copy" @click="onCopyResult">复制</view>
          <view class="result-btn result-close" @click="showResult = false">关闭</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  pageTitleKey: 'pageTitle.userScan',

  data() {
    return {
      flashOn: false,
      showResult: false,
      scanResult: '',
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onToggleFlash() {
      this.flashOn = !this.flashOn
      uni.showToast({
        title: this.flashOn ? '闪光灯已开启' : '闪光灯已关闭',
        icon: 'none',
      })
    },

    onPickAlbum() {
      uni.chooseImage({
        count: 1,
        success: (res) => {
          uni.showToast({ title: '图片已选择，扫码功能开发中', icon: 'none' })
        },
      })
    },

    onViewHistory() {
      uni.showToast({ title: '扫码记录功能开发中', icon: 'none' })
    },

    onCopyResult() {
      uni.setClipboardData({
        data: this.scanResult,
        success: () => {
          uni.showToast({ title: '已复制', icon: 'success' })
        },
      })
    },

    handleScan(code) {
      this.scanResult = code
      this.showResult = true
    },
  },
}
</script>

<style lang="scss" scoped>
.scan-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1c1c1e;
  color: #ffffff;
  position: relative;
  overflow: hidden;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 40rpx 16rpx;
  position: relative;
  z-index: 10;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(16rpx);
}

.header-icon {
  font-size: 36rpx;
  color: #ffffff;
}

.header-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

.scan-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 80rpx;
  min-height: 0;
}

.scan-frame {
  position: relative;
  width: 520rpx;
  height: 520rpx;
  border-radius: 32rpx;
  overflow: hidden;
  background: linear-gradient(180deg, #2c2c2e 0%, #1c1c1e 40%, #0a0a0a 100%);
  border: 2rpx solid rgba(255, 255, 255, 0.08);
}

.scan-line {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  height: 4rpx;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--color-primary, #007aff) 30%,
    var(--color-primary, #007aff) 70%,
    transparent 100%
  );
  box-shadow: 0 0 16rpx rgba(0, 122, 255, 0.5);
  z-index: 2;
  animation: scanLine 2.4s ease-in-out infinite;
}

.corner {
  position: absolute;
  width: 48rpx;
  height: 48rpx;

  &::before,
  &::after {
    content: '';
    position: absolute;
    background: var(--color-primary, #007aff);
    border-radius: 2rpx;
  }
}

.corner-tl {
  top: -2rpx;
  left: -2rpx;
  &::before {
    top: 0;
    left: 0;
    width: 48rpx;
    height: 6rpx;
  }
  &::after {
    top: 0;
    left: 0;
    width: 6rpx;
    height: 48rpx;
  }
}

.corner-tr {
  top: -2rpx;
  right: -2rpx;
  &::before {
    top: 0;
    right: 0;
    width: 48rpx;
    height: 6rpx;
  }
  &::after {
    top: 0;
    right: 0;
    width: 6rpx;
    height: 48rpx;
  }
}

.corner-bl {
  bottom: -2rpx;
  left: -2rpx;
  &::before {
    bottom: 0;
    left: 0;
    width: 48rpx;
    height: 6rpx;
  }
  &::after {
    bottom: 0;
    left: 0;
    width: 6rpx;
    height: 48rpx;
  }
}

.corner-br {
  bottom: -2rpx;
  right: -2rpx;
  &::before {
    bottom: 0;
    right: 0;
    width: 48rpx;
    height: 6rpx;
  }
  &::after {
    bottom: 0;
    right: 0;
    width: 6rpx;
    height: 48rpx;
  }
}

.scan-hint {
  text-align: center;
  margin-top: 48rpx;
  font-size: 28rpx;
  color: #aeaeb2;
  line-height: 1.5;
}

.bottom-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80rpx;
  padding: 48rpx 0 96rpx;
  position: relative;
  z-index: 10;
}

.bottom-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  background: none;
  border: none;
  color: #e3e3e8;
}

.btn-circle {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon {
  font-size: 36rpx;
}

.btn-label {
  font-size: 24rpx;
  color: #aeaeb2;
}

.result-modal {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
}

.result-content {
  position: relative;
  width: 600rpx;
  background: #ffffff;
  border-radius: 24rpx;
  padding: 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.result-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 24rpx;
}

.result-data {
  font-size: 28rpx;
  color: #48484a;
  text-align: center;
  word-break: break-all;
  margin-bottom: 32rpx;
  padding: 24rpx;
  background: #f2f2f7;
  border-radius: 16rpx;
  width: 100%;
}

.result-actions {
  display: flex;
  gap: 24rpx;
  width: 100%;
}

.result-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.result-copy {
  background: var(--color-primary, #007aff);
  color: #ffffff;
}

.result-close {
  background: #f2f2f7;
  color: #1d1d1f;
}

@keyframes scanLine {
  0% {
    top: 0;
  }
  50% {
    top: calc(100% - 4rpx);
  }
  100% {
    top: 0;
  }
}
</style>
