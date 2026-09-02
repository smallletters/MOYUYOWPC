<template>
  <view class="pet-hub">
    <view class="header">
      <text class="title">Pet Hub</text>
      <text class="subtitle">Companion Care Center</text>
    </view>

    <scroll-view scroll-y class="content">
      <view class="scene" @click="onSceneClick">
        <view class="scene-placeholder">
          <view class="scene-emoji">
            <text
              v-if="activePet"
              class="luc"
              :class="$luc(activePet.type === 'CAT' ? 'paw-print' : 'paw-print')"
            />
          </view>
          <text class="scene-name">{{ activePet?.name || 'No pet' }}</text>
          <text class="scene-tip">Tap to interact</text>
        </view>
      </view>

      <view class="pet-switcher">
        <view
          v-for="pet in petStore.pets"
          :key="pet.id"
          class="pet-chip"
          :class="{ active: petStore.currentPet?.id === pet.id }"
          @click="switchPet(pet)"
        >
          <text
            class="pet-emoji luc"
            :class="$luc(pet.type === 'CAT' ? 'paw-print' : 'paw-print')"
          />
          <text class="pet-name">{{ pet.name }}</text>
        </view>
        <view class="pet-chip add" @click="goAddPet">
          <text class="pet-add">+</text>
        </view>
      </view>

      <view class="care-cards">
        <view
          v-for="card in computedCareCards"
          :key="card.id"
          class="care-card"
          @click="onCareClick(card)"
        >
          <view class="care-icon" :style="{ background: card.bg }">
            <text class="luc" :class="$luc(card.icon)" />
          </view>
          <view class="care-info">
            <text class="care-title">{{ card.title }}</text>
            <text class="care-subtitle">{{ card.subtitle }}</text>
          </view>
          <text class="care-arrow luc-chevron-right" />
        </view>
      </view>

      <view class="quick-actions">
        <view class="section-title">Quick Actions</view>
        <view class="action-grid">
          <view
            v-for="a in actions"
            :key="a.id"
            class="action-item"
            @click="onActionClick(a)">
            <text class="action-icon luc" :class="$luc(a.icon)" />
            <text class="action-label">{{ a.label }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>
<script>
import { usePetStore } from '@/store'
import { useUserStore } from '@/store'

export default {
  pageTitleKey: 'pageTitle.tabbarPet',

  data() {
    return {
      actions: [
        { id: 'shop', label: 'Buy Supplies', icon: 'shopping-bag' },
        { id: 'share', label: 'Share Pet', icon: 'camera' },
        { id: 'calendar', label: 'Health Calendar', icon: 'calendar' },
        { id: 'achievement', label: 'Achievements', icon: 'trophy' },
      ],
    }
  },

  computed: {
    petStore() {
      return usePetStore()
    },
    userStore() {
      return useUserStore()
    },
    activePet() {
      return this.petStore.activePet
    },
    computedCareCards() {
      if (!this.activePet) return []
      const p = this.activePet
      return [
        {
          id: 'bath',
          title: 'Bathing',
          subtitle: p.lastBathDate ? `Last: ${p.lastBathDate}` : 'Not recorded',
          icon: 'bath',
          bg: 'rgba(219,201,138,0.15)',
        },
        {
          id: 'vaccine',
          title: 'Vaccine',
          subtitle: p.lastVaccineDate ? `Last: ${p.lastVaccineDate}` : 'Not recorded',
          icon: 'syringe',
          bg: 'rgba(171,185,173,0.15)',
        },
        {
          id: 'deworm',
          title: 'Deworming',
          subtitle: p.lastDewormDate ? `Last: ${p.lastDewormDate}` : 'Not recorded',
          icon: 'pill',
          bg: 'rgba(217,180,176,0.15)',
        },
        {
          id: 'checkup',
          title: 'Checkup',
          subtitle: `Weight: ${p.weightKg || '-'} kg`,
          icon: 'stethoscope',
          bg: 'rgba(179,138,90,0.15)',
        },
      ]
    },
  },

  onShow() {
    // 未登录不调接口,避免 Pet Tab 每次切回都打 401 噪音日志
    if (!this.userStore?.isLoggedIn) return
    this.petStore.loadPets()
  },

  methods: {
    switchPet(pet) {
      this.petStore.currentPet = pet
    },

    onSceneClick() {
      if (!this.activePet) return
      uni.showToast({ title: `${this.activePet.name} is happy to see you!`, icon: 'none' })
    },

    onCareClick(card) {
      if (!this.activePet?.id) return
      uni.navigateTo({ url: `/pages/pet/health?petId=${this.activePet.id}` })
    },

    onActionClick(action) {
      switch (action.id) {
        case 'shop':
          uni.switchTab({ url: '/pages/tabbar/category' })
          break
        case 'calendar':
          if (this.activePet?.id) {
            uni.navigateTo({ url: `/pages/pet/health?petId=${this.activePet.id}` })
          }
          break
        case 'achievement':
          if (this.activePet?.id) {
            uni.navigateTo({ url: `/pages/pet/achievement?petId=${this.activePet.id}` })
          }
          break
        default:
          uni.showToast({ title: 'Coming soon', icon: 'none' })
      }
    },

    goAddPet() {
      uni.navigateTo({ url: '/pages/pet/profile' })
    },
  },
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
  padding: 48rpx 24rpx 16rpx;
  /* 状态栏安全区：原公式只覆盖 iOS safe-area，APP 端（Android）需要再加 status-bar-height */
  padding-top: calc(48rpx + env(safe-area-inset-top, 0px) + var(--status-bar-height, 0px));
  background: var(--color-surface);
}
.title {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
}
.subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}
.content {
  flex: 1;
}
.scene {
  margin: 24rpx;
  height: 360rpx;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: linear-gradient(135deg, #2e2b29, #4a4540);
  display: flex;
  align-items: center;
  justify-content: center;
}
.scene-placeholder {
  text-align: center;
  color: #f6f2ee;
}
.scene-emoji {
  font-size: 96rpx;
  display: block;
  margin-bottom: 16rpx;
}
.scene-name {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}
.scene-tip {
  font-size: var(--font-size-xs);
  opacity: 0.5;
  margin-top: 8rpx;
  display: block;
}
.pet-switcher {
  display: flex;
  gap: 16rpx;
  padding: 0 24rpx;
  margin-bottom: 24rpx;
  flex-wrap: wrap;
}
.pet-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-pill);
  border: 2rpx solid transparent;
}
.pet-chip.active {
  border-color: var(--color-primary);
}
.pet-emoji {
  font-size: 32rpx;
}
.pet-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
}
.pet-chip.add {
  justify-content: center;
  width: 72rpx;
  padding: 12rpx 0;
}
.pet-add {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
}
.care-cards {
  padding: 0 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 24rpx;
}
.care-card {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 20rpx;
  gap: 16rpx;
}
.care-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
.care-info {
  flex: 1;
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
  font-size: 36rpx;
  color: var(--color-text-tertiary);
}
.quick-actions {
  padding: 0 24rpx 32rpx;
}
.section-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 20rpx 0;
  background: var(--color-surface);
  border-radius: var(--radius-md);
}
.action-icon {
  font-size: 40rpx;
}
.action-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}
</style>
