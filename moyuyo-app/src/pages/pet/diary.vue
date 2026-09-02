<template>
  <view class="pet-diary">
    <view class="page-header">
      <view class="back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">宠物日记</text>
      <view class="add-btn" @click="onAdd">+ 写日记</view>
    </view>

    <scroll-view scroll-y class="content">
      <view v-if="entries.length === 0" class="empty">
        <text class="empty-icon luc-book" />
        <text class="empty-text">还没有日记，写下今天的回忆吧</text>
      </view>

      <view v-else class="entry-list">
        <view v-for="e in entries" :key="e.id" class="entry-card">
          <view class="entry-header">
            <text class="entry-date">{{ e.date }}</text>
            <text class="entry-pet">{{ e.petName }}</text>
          </view>
          <text class="entry-content">{{ e.content }}</text>
          <view v-if="e.image" class="entry-image-wrap">
            <image :src="e.image" class="entry-image" />
          </view>
          <view class="entry-mood">
            <text>{{ e.mood }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { petApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.petDiary',

  data() {
    return {
      entries: [
        {
          id: 1,
          date: '今天',
          petName: '旺财',
          content: '今天带旺财去公园玩，它追蝴蝶追得很开心！',
          image: 'https://picsum.photos/300/200?random=30',
          mood: '😊 开心',
        },
        {
          id: 2,
          date: '昨天',
          petName: '旺财',
          content: '学会了新技能"坐下"，奖励了一个小饼干',
          mood: '🎉 骄傲',
        },
        {
          id: 3,
          date: '3天前',
          petName: '旺财',
          content: '体检一切正常，医生说继续保持',
          mood: '😌 安心',
        },
      ],
    }
  },

  methods: {
    goBack() {
      uni.navigateBack()
    },

    onAdd() {
      uni.showToast({ title: '日记编辑页', icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.pet-diary {
  min-height: 100vh;
  background: var(--color-background);
}

.page-header {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: var(--color-text);
}

.title {
  flex: 1;
  text-align: center;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.add-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.content {
  padding: 24rpx;
}

.empty {
  padding: 96rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.empty-icon {
  font-size: 120rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.entry-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.entry-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.entry-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.entry-date {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}

.entry-pet {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.entry-content {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  line-height: 1.6;
  margin-bottom: 12rpx;
}

.entry-image-wrap {
  margin-bottom: 12rpx;
}

.entry-image {
  width: 100%;
  border-radius: var(--radius-sm);
  aspect-ratio: 3 / 2;
  object-fit: cover;
}

.entry-mood {
  padding-top: 12rpx;
  border-top: 1rpx solid var(--color-divider);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
}
</style>
