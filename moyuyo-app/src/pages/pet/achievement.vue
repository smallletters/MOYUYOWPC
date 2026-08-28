<template>
  <view class="achievement">
    <view class="header">
      <text class="title">Achievements</text>
      <text class="subtitle">{{ unlockedCount }} / {{ achievements.length }} Unlocked</text>
    </view>

    <view class="grid">
      <view
        v-for="a in achievements"
        :key="a.achievementCode || a.id"
        class="badge"
        :class="{ unlocked: a.unlocked }"
        @click="onBadgeClick(a)"
      >
        <view class="badge-icon">
          <text>{{ achievementIcon(a.achievementCode) }}</text>
        </view>
        <text class="badge-name">{{ achievementName(a.achievementCode) }}</text>
        <text class="badge-desc">
          {{ a.unlocked ? achievementDesc(a.achievementCode) : 'Locked' }}
        </text>
        <view v-if="!a.unlocked" class="progress-bar">
          <view class="progress-fill" :style="{ width: `${(a.progress || 0) * 100}%` }" />
        </view>
      </view>
    </view>
  </view>
</template>
<script>
import { petApi } from '@/api'

const ACHIEVEMENT_MAP = {
  CLEAN_BABY: { icon: 'bath', name: 'Clean Baby', desc: 'Keep bath schedule on track' },
  EPIDEMIC_MASTER: { icon: 'syringe', name: 'Epidemic Master', desc: 'Vaccinations up to date' },
  DEWORM_GUARDIAN: { icon: 'pill', name: 'Deworm Guardian', desc: 'Regular deworming completed' },
  ATTENDANCE_FULL: {
    icon: 'calendar',
    name: 'Full Attendance',
    desc: 'All care reminders checked',
  },
  EXPLORER_PIONEER: { icon: 'map', name: 'Explorer Pioneer', desc: 'Unlocked all 3D scenes' },
}

export default {
  data() {
    return {
      petId: null,
      achievements: [],
    }
  },

  computed: {
    unlockedCount() {
      return this.achievements.filter((a) => a.unlocked).length
    },
  },

  onLoad(query) {
    this.petId = query.petId || null
    this.loadAchievements()
  },

  methods: {
    async loadAchievements() {
      if (!this.petId) return
      try {
        this.achievements = await petApi.getAchievements(this.petId)
      } catch (e) {
        console.warn('[achievement] load failed', e)
        this.achievements = []
      }
    },

    achievementIcon(code) {
      return ACHIEVEMENT_MAP[code]?.icon || 'trophy'
    },
    achievementName(code) {
      return ACHIEVEMENT_MAP[code]?.name || code
    },
    achievementDesc(code) {
      return ACHIEVEMENT_MAP[code]?.desc || ''
    },

    onBadgeClick(a) {
      uni.showToast({
        title: a.unlocked
          ? this.achievementName(a.achievementCode)
          : `${(a.progress || 0) * 100}% complete`,
        icon: 'none',
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.achievement {
  min-height: 100vh;
  background: var(--color-background);
  padding: 32rpx 16rpx;
  padding-top: calc(32rpx + env(safe-area-inset-top));
}
.header {
  padding: 0 16rpx 24rpx;
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
.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding: 0 8rpx;
}
.badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 32rpx 16rpx;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  text-align: center;
}
.badge.unlocked {
}
.badge:not(.unlocked) {
  opacity: 0.5;
}
.badge-icon {
  font-size: 56rpx;
}
.badge-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
}
.badge-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.progress-bar {
  width: 100%;
  height: 6rpx;
  background: var(--color-divider);
  border-radius: 3rpx;
  overflow: hidden;
  margin-top: 4rpx;
}
.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 3rpx;
}
</style>
