<template>
  <view class="achievement">
    <view class="header">
      <text class="title">Achievements</text>
      <text class="subtitle">{{ unlockedCount }} / {{ achievements.length }} Unlocked</text>
    </view>

    <view class="grid">
      <view
        v-for="a in achievements"
        :key="a.id"
        class="badge"
        :class="{ unlocked: a.unlocked }"
        @click="onBadgeClick(a)"
      >
        <view class="badge-icon">
          <text>{{ a.icon }}</text>
        </view>
        <text class="badge-name">{{ a.name }}</text>
        <text class="badge-desc">{{ a.unlocked ? a.desc : a.lockDesc }}</text>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      achievements: [
        { id: 1, name: 'First Friend', desc: 'Added your first pet', lockDesc: 'Add a pet to unlock', icon: '🐾', unlocked: true },
        { id: 2, name: '5-Star Friend', desc: 'Got your first 5-star review', lockDesc: 'Write a review to unlock', icon: '⭐', unlocked: true },
        { id: 3, name: 'Explorer', desc: 'Walked 100km with your pet', lockDesc: 'Track walks to unlock', icon: '🗺️', unlocked: false },
        { id: 4, name: 'Healthy Buddy', desc: 'Completed 1 year of care reminders', lockDesc: 'Stay on schedule to unlock', icon: '💚', unlocked: true },
        { id: 5, name: 'Social Star', desc: 'Got 100 likes on a post', lockDesc: 'Share posts to unlock', icon: '✨', unlocked: false },
        { id: 6, name: 'Loyal Customer', desc: 'Made 10 orders', lockDesc: 'Keep shopping to unlock', icon: '👑', unlocked: true },
        { id: 7, name: 'IP Collector', desc: 'Own products from all 4 IPs', lockDesc: 'Collect all IPs to unlock', icon: '🌟', unlocked: false },
        { id: 8, name: 'Reviewer Pro', desc: 'Wrote 50 reviews', lockDesc: 'Write more reviews to unlock', icon: '✍️', unlocked: false }
      ]
    }
  },

  computed: {
    unlockedCount() {
      return this.achievements.filter((a) => a.unlocked).length
    }
  },

  methods: {
    onBadgeClick(a) {
      uni.showToast({
        title: a.unlocked ? a.name : a.lockDesc,
        icon: 'none',
        duration: 2000
      })
    }
  }
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
  display: block;
  font-size: 40rpx;
  font-weight: var(--font-weight-bold);
}

.subtitle {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: 4rpx;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
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
  opacity: 0.5;
  filter: grayscale(1);
}

.badge.unlocked {
  opacity: 1;
  filter: none;
}

.badge-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #DBC98A 0%, #B38A5A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  box-shadow: 0 4rpx 16rpx rgba(219, 201, 138, 0.3);
}

.badge-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  margin-top: 8rpx;
}

.badge-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.4;
}
</style>
