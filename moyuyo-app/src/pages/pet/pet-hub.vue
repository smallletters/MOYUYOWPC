<template>
  <view class="pet-hub">
    <view class="page-header">
      <text class="header-title">Pet Hub</text>
      <view class="header-icon" @click="goSettings" aria-label="设置">⚙</view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 宠物档案卡片 -->
      <view class="profile-card">
        <view class="profile-row">
          <view class="avatar-wrap">
            <image :src="currentPet?.avatar || ''" class="avatar" />
          </view>
          <view class="profile-info">
            <text class="pet-name">{{ currentPet?.name || '添加宠物' }}</text>
            <text class="pet-breed">{{ currentPet?.breed || '点击下方添加' }}</text>
            <view class="pet-meta">
              <text>{{ currentPet?.gender || '—' }}</text>
              <text>{{ currentPet?.age || '—' }}</text>
              <text>{{ currentPet?.weight || '—' }}</text>
            </view>
          </view>
        </view>
        <view class="action-row">
          <view class="action-btn primary" @click="goAddPet">
            <text>+ 添加宠物</text>
          </view>
          <view class="action-btn" @click="goEditPet">
            <text>编辑资料</text>
          </view>
          <view class="action-btn" @click="onShare">
            <text>分享</text>
          </view>
        </view>
      </view>

      <!-- 护理提醒 -->
      <view class="section">
        <text class="section-title">护理提醒</text>
        <scroll-view scroll-x class="remind-scroll">
          <view class="remind-list">
            <view class="remind-card">
              <view class="remind-top">
                <view class="remind-icon">⚠</view>
                <text class="remind-days">12天</text>
              </view>
              <text class="remind-name">驱虫</text>
              <text class="remind-desc">下次驱虫提醒</text>
            </view>
            <view class="remind-card">
              <view class="remind-top">
                <view class="remind-icon success">✓</view>
                <text class="remind-days">25天</text>
              </view>
              <text class="remind-name">疫苗</text>
              <text class="remind-desc">下次疫苗提醒</text>
            </view>
            <view class="remind-card">
              <view class="remind-top">
                <view class="remind-icon">🛁</view>
                <text class="remind-days">7天</text>
              </view>
              <text class="remind-name">洗澡</text>
              <text class="remind-desc">下次洗澡提醒</text>
            </view>
            <view class="remind-card">
              <view class="remind-top">
                <view class="remind-icon">🦷</view>
                <text class="remind-days">15天</text>
              </view>
              <text class="remind-name">口腔</text>
              <text class="remind-desc">下次口腔护理</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 快捷入口 -->
      <view class="section">
        <text class="section-title">快捷入口</text>
        <view class="quick-grid">
          <view class="quick-item" v-for="q in quickEntries" :key="q.id" @click="onQuickClick(q)">
            <text class="quick-icon">{{ q.icon }}</text>
            <text class="quick-label">{{ q.label }}</text>
          </view>
        </view>
      </view>

      <!-- 宠物列表 -->
      <view class="section">
        <text class="section-title">我的宠物</text>
        <view v-if="pets.length === 0" class="empty">
          <text>暂无宠物，点击"添加宠物"创建档案</text>
        </view>
        <view v-else class="pet-list">
          <view v-for="p in pets" :key="p.id" class="pet-card">
            <image :src="p.avatar" class="pet-avatar" />
            <view class="pet-card-info">
              <text class="pet-card-name">{{ p.name }}</text>
              <text class="pet-card-breed">{{ p.breed }}</text>
            </view>
            <text class="pet-card-arrow">›</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi } from '@/api'

export default {
  data() {
    return {
      pets: [],
      currentPet: null,
      quickEntries: [
        { id: 'album', label: '相册', icon: '📷' },
        { id: 'diary', label: '日记', icon: '📝' },
        { id: 'dresser', label: '装扮', icon: '👗' },
        { id: 'health', label: '健康', icon: '🏥' },
        { id: 'weight', label: '体重', icon: '⚖' },
        { id: 'calendar', label: '日历', icon: '📅' },
      ],
    }
  },

  onShow() {
    this.loadPets()
  },

  methods: {
    async loadPets() {
      try {
        const list = await petApi.getPetList()
        this.pets = Array.isArray(list) ? list : []
        this.currentPet = this.pets[0] || null
      } catch (e) {
        console.warn('[pet-hub] load failed', e)
      }
    },

    goSettings() {
      uni.navigateTo({ url: '/pages/user/settings' })
    },

    goAddPet() {
      uni.navigateTo({ url: '/pages/pet/profile' })
    },

    goEditPet() {
      if (this.currentPet) {
        uni.navigateTo({ url: `/pages/pet/profile?id=${this.currentPet.id}` })
      }
    },

    onShare() {
      uni.showToast({ title: '请使用右上角分享', icon: 'none' })
    },

    onQuickClick(q) {
      const map = {
        album: '/pages/pet/album',
        diary: '/pages/pet/diary',
        dresser: '/pages/pet/dresser',
        health: '/pages/pet/health-calendar',
        weight: '/pages/pet/weight-chart',
        calendar: '/pages/pet/health-calendar',
      }
      if (map[q.id]) {
        uni.navigateTo({ url: map[q.id] })
      } else {
        uni.showToast({ title: '敬请期待', icon: 'none' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-hub {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.header-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.header-icon {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: var(--color-text);
}

.content {
  padding: 24rpx;
}

.profile-card {
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 24rpx;
  padding: 32rpx 24rpx;
  margin-bottom: 32rpx;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-wrap {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  padding: 4rpx;
  background: var(--color-primary);
}

.avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--color-background);
  border: 2rpx solid var(--color-surface);
}

.profile-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.pet-name {
  font-size: 36rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.pet-breed {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.pet-meta {
  display: flex;
  gap: 16rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 8rpx;
}

.action-row {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-background);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.action-btn.primary {
  background: var(--color-primary);
  color: var(--color-text);
}

.section-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 20rpx;
}

.section {
  margin-bottom: 32rpx;
}

.remind-scroll {
  white-space: nowrap;
}

.remind-list {
  display: inline-flex;
  gap: 16rpx;
}

.remind-card {
  display: inline-flex;
  flex-direction: column;
  width: 240rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 20rpx;
}

.remind-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.remind-icon {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: var(--radius-md);
  font-size: 28rpx;
}

.remind-icon.success {
  background: rgba(52, 199, 89, 0.15);
  color: #34c759;
}

.remind-days {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-semibold);
}

.remind-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.remind-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: 24rpx;
  padding: 24rpx;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 0;
}

.quick-icon {
  font-size: 44rpx;
}

.quick-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.empty {
  text-align: center;
  padding: 48rpx 0;
  background: var(--color-surface);
  border: 1rpx dashed var(--color-divider);
  border-radius: 24rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.pet-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.pet-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.pet-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--color-background);
}

.pet-card-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.pet-card-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.pet-card-breed {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.pet-card-arrow {
  font-size: 32rpx;
  color: var(--color-text-tertiary);
}
</style>