<template>
  <view class="topic-square">
    <view class="page-header">
      <view class="back" @click="goBack" aria-label="返回">‹</view>
      <text class="title">话题广场</text>
      <view class="post-btn" @click="onCreate">+ 发布</view>
    </view>

    <scroll-view scroll-y class="content">
      <!-- 热门话题 -->
      <view class="section">
        <text class="section-title">🔥 热门话题</text>
        <view class="topic-grid">
          <view v-for="t in hotTopics" :key="t.id" class="topic-card" @click="onTopicClick(t)">
            <text class="topic-name">#{{ t.name }}</text>
            <text class="topic-meta">{{ t.posts }} 帖子 · {{ t.views }} 浏览</text>
          </view>
        </view>
      </view>

      <!-- 分类 -->
      <view class="cat-tabs">
        <view
          v-for="c in categories"
          :key="c.id"
          class="cat-tab"
          :class="{ active: activeCat === c.id }"
          @click="activeCat = c.id"
        >
          {{ c.label }}
        </view>
      </view>

      <!-- 帖子列表 -->
      <view class="post-list">
        <view v-for="p in posts" :key="p.id" class="post-card">
          <view class="post-header">
            <image :src="p.avatar" class="post-avatar" />
            <view class="post-info">
              <text class="post-name">{{ p.name }}</text>
              <text class="post-time">{{ p.time }}</text>
            </view>
          </view>
          <text class="post-content">{{ p.content }}</text>
          <view v-if="p.image" class="post-image-wrap">
            <image :src="p.image" class="post-image" />
          </view>
          <view class="post-actions">
            <text>♥ {{ p.likes }}</text>
            <text>💬 {{ p.comments }}</text>
            <text>↗ 分享</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { communityApi } from '@/api'

export default {
  data() {
    return {
      activeCat: 'all',
      categories: [
        { id: 'all', label: '全部' },
        { id: 'share', label: '晒单' },
        { id: 'qa', label: '问答' },
        { id: 'experience', label: '体验' },
      ],
      hotTopics: [
        { id: 1, name: '金毛日常', posts: 328, views: '12.5k' },
        { id: 2, name: '猫咪好物', posts: 512, views: '20.1k' },
        { id: 3, name: '新手养宠', posts: 89, views: '5.6k' },
        { id: 4, name: '宠物医疗', posts: 196, views: '8.3k' },
      ],
      posts: [],
    }
  },

  onShow() {
    this.loadPosts()
  },

  methods: {
    async loadPosts() {
      this.posts = [
        {
          id: 1,
          name: '宠物达人A',
          avatar: 'https://i.pravatar.cc/100?img=11',
          time: '2小时前',
          content: '今天带旺财去公园玩耍，这小家伙太兴奋了！',
          image: 'https://picsum.photos/400/300?random=10',
          likes: 32,
          comments: 8,
        },
        {
          id: 2,
          name: '猫奴小李',
          avatar: 'https://i.pravatar.cc/100?img=12',
          time: '4小时前',
          content: '推荐这款猫粮，猫咪吃得很香！',
          likes: 56,
          comments: 12,
        },
      ]
    },

    goBack() {
      uni.navigateBack()
    },

    onCreate() {
      uni.navigateTo({ url: '/pages/community/post-create' })
    },

    onTopicClick(t) {
      uni.showToast({ title: `#${t.name}`, icon: 'none' })
    },
  },
}
</script>

<style lang="scss" scoped>
.topic-square {
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

.post-btn {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
}

.content {
  padding: 24rpx;
}

.section-title {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
  margin-bottom: 16rpx;
}

.topic-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.topic-card {
  padding: 20rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.topic-name {
  display: block;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  margin-bottom: 8rpx;
}

.topic-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.cat-tabs {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  margin-bottom: 16rpx;
  overflow-x: auto;
}

.cat-tab {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.cat-tab.active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.post-card {
  padding: 24rpx;
  background: var(--color-surface);
  border: 1rpx solid var(--color-divider);
  border-radius: var(--radius-md);
}

.post-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.post-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--color-background);
}

.post-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.post-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.post-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.post-content {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text);
  line-height: 1.6;
  margin-bottom: 12rpx;
}

.post-image-wrap {
  margin-bottom: 12rpx;
}

.post-image {
  width: 100%;
  border-radius: var(--radius-sm);
  aspect-ratio: 4 / 3;
  object-fit: cover;
}

.post-actions {
  display: flex;
  gap: 32rpx;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>