<template>
  <view class="community">
    <view class="header">
      <text class="title">Community</text>
    </view>

    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="activeTab = t.value"
      >
        {{ t.label }}
      </view>
    </view>

    <scroll-view scroll-y class="feed">
      <view v-for="post in posts" :key="post.id" class="post-card">
        <view class="post-header">
          <image :src="post.avatar" class="post-avatar" />
          <view class="post-user">
            <text class="post-username">{{ post.username }}</text>
            <text class="post-time">{{ post.time }}</text>
          </view>
        </view>
        <text class="post-content">{{ post.content }}</text>
        <image v-if="post.image" :src="post.image" class="post-image" mode="widthFix" />
        <view class="post-actions">
          <view class="post-action" @click="onLike(post)">
            <text>♥</text> <text>{{ post.likes }}</text>
          </view>
          <view class="post-action">
            <text>💬</text> <text>{{ post.comments }}</text>
          </view>
          <view class="post-action">
            <text>↗</text> <text>Share</text>
          </view>
        </view>
      </view>

      <view v-if="!posts.length" class="empty">No posts yet</view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      activeTab: 'recommend',
      tabs: [
        { value: 'recommend', label: '推荐' },
        { value: 'follow', label: '关注' },
        { value: 'topic', label: '话题' }
      ],
      posts: [
        {
          id: 1,
          username: 'MILO探险家',
          avatar: 'https://i.pravatar.cc/100?img=1',
          time: '2小时前',
          content: '今天带我家灵缇去爬山！穿了 MOYUYO 的反光胸背超帅气 ✨',
          image: 'https://picsum.photos/600/600?random=10',
          likes: 128,
          comments: 23
        },
        {
          id: 2,
          username: 'LUNA策展家',
          avatar: 'https://i.pravatar.cc/100?img=2',
          time: '5小时前',
          content: '新入的洗护套装到了，包装好有质感 🌿',
          image: 'https://picsum.photos/600/600?random=11',
          likes: 256,
          comments: 45
        }
      ]
    }
  },

  methods: {
    onLike(post) {
      post.likes += 1
      uni.showToast({ title: 'Liked!', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.community {
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
}

.tabs {
  display: flex;
  background: var(--color-surface);
  padding: 0 24rpx;
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  padding: 24rpx 0;
  margin-right: 48rpx;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  position: relative;
}

.tab.active {
  color: var(--color-text);
  font-weight: var(--font-weight-semibold);
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32rpx;
  height: 4rpx;
  background: var(--color-primary);
  border-radius: 2rpx;
}

.feed {
  flex: 1;
  padding: 16rpx;
}

.post-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 24rpx;
  margin-bottom: 16rpx;
}

.post-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.post-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
}

.post-user {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.post-username {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.post-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.post-content {
  display: block;
  margin: 16rpx 0;
  font-size: var(--font-size-base);
  line-height: 1.6;
}

.post-image {
  width: 100%;
  border-radius: var(--radius-sm);
}

.post-actions {
  display: flex;
  gap: 48rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--color-divider);
}

.post-action {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.empty {
  text-align: center;
  padding: 96rpx 0;
  color: var(--color-text-tertiary);
}
</style>
