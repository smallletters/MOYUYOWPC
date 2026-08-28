<template>
  <view class="onboarding">
    <!-- 跳过按钮 -->
    <text class="skip-btn" @click="goToHome">跳过</text>
    <!-- 滑动区域 -->
    <swiper
      class="swiper"
      :current="currentIndex"
      :circular="false"
      :disable-touch="false"
      @change="onSwiperChange"
    >
      <swiper-item v-for="(slide, index) in slides" :key="index">
        <view class="slide">
          <!-- 插画占位区域 -->
          <view class="slide-image" :style="{ background: slide.bg }">
            <text class="slide-emoji">{{ slide.emoji }}</text>
          </view>
          <!-- 文字内容 -->
          <view class="slide-content">
            <text class="slide-title">{{ slide.title }}</text>
            <text class="slide-desc">{{ slide.desc }}</text>
          </view>
        </view>
      </swiper-item>
    </swiper>
    <!-- 底部操作区 -->
    <view class="footer">
      <!-- 页码指示器 -->
      <view class="indicators">
        <view
          v-for="(slide, index) in slides"
          :key="index"
          class="dot"
          :class="{ active: currentIndex === index }"
        />
      </view>
      <!-- 按钮 -->
      <button
        class="action-btn"
        :class="currentIndex === slides.length - 1 ? 'btn-primary' : 'btn-secondary'"
        @click="onActionClick"
      >
        {{ currentIndex === slides.length - 1 ? '立即开始' : '下一步' }}
      </button>
    </view>
  </view>
</template>
<script>
export default {
  data() {
    return {
      currentIndex: 0,
      slides: [
        {
          title: '欢迎来到 MOYUYO',
          desc: '为您的宠物提供最优质的产品与服务，开启美好养宠生活',
          emoji: 'paw-print',
          bg: 'linear-gradient(135deg, var(--color-primary-light), var(--color-background))',
        },
        {
          title: '发现优质宠物好物',
          desc: '精选全球优质宠物用品，从食品到配件，一站式购齐',
          emoji: 'shopping-bag',
          bg: 'linear-gradient(135deg, var(--color-info), var(--color-background))',
        },
        {
          title: '追踪宠物健康',
          desc: '记录宠物体重、疫苗、驱虫等健康数据，科学养宠更省心',
          emoji: 'bar-chart',
          bg: 'linear-gradient(135deg, var(--color-success), var(--color-background))',
        },
        {
          title: '加入我们的社区',
          desc: '与千万宠物主分享养宠经验，记录与TA的每一个美好瞬间',
          emoji: 'heart',
          bg: 'linear-gradient(135deg, var(--color-warm), var(--color-background))',
        },
      ],
    }
  },

  methods: {
    onSwiperChange(e) {
      this.currentIndex = e.detail.current
    },

    onActionClick() {
      if (this.currentIndex < this.slides.length - 1) {
        this.currentIndex++
      } else {
        this.goToHome()
      }
    },

    goToHome() {
      uni.reLaunch({
        url: '/pages/tabbar/home',
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.onboarding {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.skip-btn {
  position: absolute;
  top: calc(env(safe-area-inset-top) + 16rpx);
  right: 32rpx;
  z-index: 10;
  font-size: 30rpx;
  color: var(--color-text-tertiary);
  padding: 16rpx 8rpx;
}

.swiper {
  flex: 1;
}

.slide {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.slide-image {
  width: 100%;
  height: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.slide-emoji {
  font-size: 160rpx;
  line-height: 1;
}

.slide-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 64rpx;
  text-align: center;
}

.slide-title {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.29;
  letter-spacing: -0.02em;
  color: var(--color-text);
  margin-bottom: 24rpx;
}

.slide-desc {
  font-size: 28rpx;
  font-weight: 400;
  line-height: 1.57;
  color: var(--color-text-secondary);
  max-width: 560rpx;
}

.footer {
  padding: 0 40rpx calc(env(safe-area-inset-bottom) + 48rpx) 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40rpx;
}

.indicators {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-divider);
  transition: all 0.35s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.dot.active {
  width: 48rpx;
  border-radius: 8rpx;
  background: var(--color-primary);
}

.action-btn {
  width: 100%;
  height: 96rpx;
  border-radius: 48rpx;
  border: none;
  font-size: 32rpx;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn.btn-secondary {
  background: var(--color-surface);
  color: var(--color-text);
  border: 1rpx solid var(--color-divider);
}

.action-btn.btn-primary {
  background: var(--color-primary);
  color: #fff;
}

.action-btn::after {
  border: none;
}
</style>
