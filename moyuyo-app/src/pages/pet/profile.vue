<template>
  <view class="pet-profile">
    <view class="header">
      <view class="avatar-section">
        <view class="avatar">{{ currentPet.emoji || '🐕' }}</view>
        <text class="name">{{ currentPet.name }}</text>
        <text class="species">{{ currentPet.species }} · {{ currentPet.breed }}</text>
      </view>
    </view>

    <view class="info-card card">
      <view class="info-row">
        <text class="label">Birthday</text>
        <text class="value">{{ currentPet.birthday || 'Unknown' }}</text>
      </view>
      <view class="info-row">
        <text class="label">Weight</text>
        <text class="value">{{ currentPet.weight || '--' }} kg</text>
      </view>
      <view class="info-row">
        <text class="label">Gender</text>
        <text class="value">{{ currentPet.gender || '--' }}</text>
      </view>
    </view>

    <!-- 体重曲线图（占位） -->
    <view class="card weight-card">
      <text class="card-title">Weight Trend</text>
      <view class="weight-chart">
        <text class="chart-placeholder">📈 Weight chart (placeholder)</text>
      </view>
    </view>

    <!-- 成长记录时间线 -->
    <view class="card timeline-card">
      <text class="card-title">Growth Timeline</text>
      <view v-for="t in timeline" :key="t.id" class="timeline-row">
        <text class="timeline-date">{{ t.date }}</text>
        <text class="timeline-event">{{ t.event }}</text>
      </view>
    </view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-outline" @click="onEdit">Edit</view>
      <view class="btn btn-primary" @click="onAddTimeline">+ Add Record</view>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      currentPet: {
        name: 'MILO',
        emoji: '🐕',
        species: 'Dog',
        breed: 'Italian Greyhound',
        birthday: '2024-03-15',
        weight: 5.2,
        gender: 'Male'
      },
      timeline: [
        { id: 1, date: '2026-07-01', event: 'Bathing day' },
        { id: 2, date: '2026-06-15', event: 'Vaccination: Rabies' },
        { id: 3, date: '2026-05-20', event: 'First hiking trip' }
      ]
    }
  },

  methods: {
    onEdit() {
      uni.showToast({ title: '编辑功能开发中', icon: 'none' })
    },
    onAddTimeline() {
      uni.showToast({ title: '新增记录功能开发中', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.pet-profile {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
}

.header {
  background: var(--color-surface);
  padding: 64rpx 24rpx 48rpx;
  padding-top: calc(64rpx + env(safe-area-inset-top));
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.avatar {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #DBC98A 0%, #B38A5A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 100rpx;
  box-shadow: 0 8rpx 24rpx rgba(219, 201, 138, 0.3);
}

.name {
  font-size: 40rpx;
  font-weight: var(--font-weight-bold);
}

.species {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin: 16rpx;
  padding: 24rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.card-title {
  display: block;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
}

.weight-chart {
  height: 200rpx;
  background: var(--color-background);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-placeholder {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.timeline-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}

.timeline-row:last-child {
  border-bottom: none;
}

.timeline-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  width: 160rpx;
}

.timeline-event {
  flex: 1;
  font-size: var(--font-size-sm);
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}

.bottom-bar .btn {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
}
</style>
