<template>
  <view class="splash">
    <!-- 品牌名 -->
    <text class="brand-name" :style="brandStyle">MOYUYO</text>
    <!-- 标语 -->
    <text class="tagline" :style="taglineStyle">为TA创造美好生活</text>
    <!-- IP 角色图标行 -->
    <view class="mascots" :style="mascotsStyle">
      <view
        class="mascot-item"
        style="background: var(--color-primary-light); color: var(--color-primary)"
      >
        M
      </view>
      <view
        class="mascot-item"
        style="background: var(--color-divider); color: var(--color-text-tertiary)"
      >
        L
      </view>
      <view
        class="mascot-item"
        style="background: var(--color-primary-light); color: var(--color-primary-dark)"
      >
        A
      </view>
      <view
        class="mascot-item"
        style="background: var(--color-divider); color: var(--color-text-tertiary)"
      >
        O
      </view>
    </view>
    <!-- 底部加载区 -->
    <view class="loading-area" :style="loadingStyle">
      <text class="loading-text">正在加载...</text>
      <view class="loading-bar">
        <view class="loading-bar-inner" />
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      brandStyle: {},
      taglineStyle: {},
      mascotsStyle: {},
      loadingStyle: {},
    }
  },

  onReady() {
    setTimeout(() => {
      this.brandStyle = {
        opacity: 1,
        transform: 'translateY(0)',
      }
    }, 100)
    setTimeout(() => {
      this.taglineStyle = {
        opacity: 1,
        transform: 'translateY(0)',
      }
    }, 350)
    setTimeout(() => {
      this.mascotsStyle = {
        opacity: 1,
        transform: 'translateY(0)',
      }
    }, 600)
    setTimeout(() => {
      this.loadingStyle = {
        opacity: 1,
        transform: 'translateY(0)',
      }
    }, 900)
    setTimeout(() => {
      uni.reLaunch({
        url: '/pages/user/onboarding',
      })
    }, 2500)
  },
}
</script>

<style lang="scss" scoped>
.splash {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--color-background);
  overflow: hidden;
}

.brand-name {
  position: relative;
  z-index: 10;
  font-size: 72rpx;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--color-text);
  opacity: 0;
  transform: translateY(32rpx);
  transition:
    opacity 0.8s cubic-bezier(0.32, 0.72, 0, 1),
    transform 0.8s cubic-bezier(0.32, 0.72, 0, 1);
}

.tagline {
  position: relative;
  z-index: 10;
  margin-top: 24rpx;
  font-size: 28rpx;
  font-weight: 400;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary);
  opacity: 0;
  transform: translateY(32rpx);
  transition:
    opacity 0.8s cubic-bezier(0.32, 0.72, 0, 1) 0.25s,
    transform 0.8s cubic-bezier(0.32, 0.72, 0, 1) 0.25s;
}

.mascots {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 32rpx;
  margin-top: 64rpx;
  opacity: 0;
  transform: translateY(32rpx);
  transition:
    opacity 0.8s cubic-bezier(0.32, 0.72, 0, 1) 0.5s,
    transform 0.8s cubic-bezier(0.32, 0.72, 0, 1) 0.5s;
}

.mascot-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  font-size: 28rpx;
  font-weight: 700;
}

.loading-area {
  position: absolute;
  bottom: 128rpx;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 10;
  opacity: 0;
  transform: translateY(32rpx);
  transition:
    opacity 0.6s cubic-bezier(0.32, 0.72, 0, 1) 0.8s,
    transform 0.6s cubic-bezier(0.32, 0.72, 0, 1) 0.8s;
}

.loading-text {
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  letter-spacing: 0.02em;
}

.loading-bar {
  margin-top: 16rpx;
  width: 240rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--color-divider);
  overflow: hidden;
}

.loading-bar-inner {
  height: 100%;
  width: 40%;
  border-radius: 2rpx;
  background: var(--color-primary);
  animation: loadingBar 1.8s cubic-bezier(0.32, 0.72, 0, 1) 0.9s infinite;
}

@keyframes loadingBar {
  0% {
    transform: translateX(-100%);
    width: 40%;
  }
  50% {
    width: 60%;
  }
  100% {
    transform: translateX(350%);
    width: 40%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-name,
  .tagline,
  .mascots,
  .loading-area {
    transition-duration: 0.01ms !important;
  }
  .loading-bar-inner {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
