<template>
  <view class="pet-hub">
    <view class="header">
      <text class="title">Pet Hub</text>
      <text class="subtitle">Companion Care Center</text>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 3D 互动区（占屏 50%） -->
      <view class="scene" @click="onSceneClick">
        <view class="scene-placeholder">
          <text class="scene-emoji">{{ currentPet.emoji || '🐕' }}</text>
          <text class="scene-name">{{ currentPet.name }}</text>
          <text class="scene-tip">Tap to interact</text>
        </view>
        <view class="scene-hint">
          <text>3D 互动场景（WebView + Three.js 占位）</text>
        </view>
      </view>

      <!-- 宠物切换器 -->
      <view class="pet-switcher">
        <view
          v-for="pet in pets"
          :key="pet.id"
          class="pet-chip"
          :class="{ active: currentPet.id === pet.id }"
          @click="currentPet = pet"
        >
          <text class="pet-emoji">{{ pet.emoji }}</text>
          <text class="pet-name">{{ pet.name }}</text>
        </view>
        <view class="pet-chip add" @click="goAddPet">
          <text class="pet-add">+</text>
        </view>
      </view>

      <!-- 护理卡片 -->
      <view class="care-cards">
        <view
          v-for="card in careCards"
          :key="card.id"
          class="care-card"
          @click="onCareClick(card)"
        >
          <view class="care-icon" :style="{ background: card.bg }">
            <text>{{ card.icon }}</text>
          </view>
          <view class="care-info">
            <text class="care-title">{{ card.title }}</text>
            <text class="care-subtitle">{{ card.subtitle }}</text>
          </view>
          <text class="care-arrow">›</text>
        </view>
      </view>

      <!-- 快捷操作区 -->
      <view class="quick-actions">
        <view class="section-title">Quick Actions</view>
        <view class="action-grid">
          <view
            v-for="a in actions"
            :key="a.id"
            class="action-item"
            @click="onActionClick(a)"
          >
            <text class="action-icon">{{ a.icon }}</text>
            <text class="action-label">{{ a.label }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      pets: [
        { id: 1, name: 'MILO', emoji: '🐕', species: 'dog' },
        { id: 2, name: 'LUNA', emoji: '🐈', species: 'cat' }
      ],
      currentPet: { id: 1, name: 'MILO', emoji: '🐕' },
      careCards: [
        { id: 'bath', title: 'Bathing', subtitle: 'Next: in 5 days', icon: '🛁', bg: 'rgba(219,201,138,0.15)' },
        { id: 'vaccine', title: 'Vaccine', subtitle: 'Next: in 30 days', icon: '💉', bg: 'rgba(171,185,173,0.15)' },
        { id: 'deworm', title: 'Deworming', subtitle: 'Next: in 14 days', icon: '💊', bg: 'rgba(217,180,176,0.15)' },
        { id: 'checkup', title: 'Checkup', subtitle: 'Next: in 90 days', icon: '🩺', bg: 'rgba(179,138,90,0.15)' }
      ],
      actions: [
        { id: 'shop', label: 'Buy Supplies', icon: '🛍️' },
        { id: 'share', label: 'Share Pet', icon: '📸' },
        { id: 'calendar', label: 'Health Calendar', icon: '📅' },
        { id: 'achievement', label: 'Achievements', icon: '🏆' }
      ]
    }
  },

  methods: {
    onSceneClick() {
      uni.showToast({ title: `${this.currentPet.name} 很开心见到你!`, icon: 'none' })
      // 实际项目中：通过 WebView 加载 Three.js 场景或 Lottie 动画
    },

    onCareClick(card) {
      uni.showToast({ title: `${card.title} 详情`, icon: 'none' })
    },

    onActionClick(action) {
      if (action.id === 'shop') {
        uni.switchTab({ url: '/pages/tabbar/category' })
      } else if (action.id === 'calendar') {
        uni.navigateTo({ url: '/pages/pet/health' })
      } else if (action.id === 'achievement') {
        uni.navigateTo({ url: '/pages/pet/achievement' })
      } else {
        uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    },

    goAddPet() {
      uni.navigateTo({ url: '/pages/pet/profile' })
    }
  }
}
</script>

<style lang="scss" scoped>
.pet-hub {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.header {
  padding: 32rpx 24rpx 16rpx;
  background: var(--color-surface);
  padding-top: calc(32rpx + env(safe-area-inset-top));
}

.title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.content {
  flex: 1;
}

.scene {
  margin: 24rpx;
  height: 50vh;
  background: linear-gradient(135deg, #F6F2EE 0%, #EAE5DD 100%);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.scene-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.scene-emoji {
  font-size: 200rpx;
  filter: drop-shadow(0 8rpx 24rpx rgba(0,0,0,0.1));
}

.scene-name {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.scene-tip {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.scene-hint {
  position: absolute;
  bottom: 16rpx;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 20rpx;
  color: var(--color-text-tertiary);
}

.pet-switcher {
  display: flex;
  gap: 16rpx;
  padding: 0 24rpx;
  overflow-x: auto;
}

.pet-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: var(--color-surface);
  border-radius: var(--radius-pill);
  flex-shrink: 0;
}

.pet-chip.active {
  background: var(--color-primary);
}

.pet-emoji { font-size: 28rpx; }
.pet-name { font-size: var(--font-size-sm); }

.pet-chip.add {
  width: 60rpx;
  height: 60rpx;
  padding: 0;
  border-radius: 50%;
  justify-content: center;
}

.pet-add {
  font-size: 36rpx;
  color: var(--color-text-tertiary);
}

.care-cards {
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.care-card {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  gap: 16rpx;
}

.care-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
}

.care-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.care-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.care-subtitle {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.care-arrow {
  font-size: 40rpx;
  color: var(--color-text-tertiary);
}

.quick-actions {
  padding: 24rpx;
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.action-icon {
  font-size: 48rpx;
}

.action-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}
</style>
