<template>
  <view class="post-detail">
    <view v-if="!post" class="loading">Loading...</view>
    <template v-else>
      <view class="post-card">
        <view class="post-header">
          <image :src="post.avatar || defaultAvatar" class="avatar" />
          <view class="user-info">
            <text class="username">{{ post.username || 'Pet Lover' }}</text>
            <text class="time">{{ formatTime(post.createTime) }}</text>
          </view>
        </view>
        <text class="content">{{ post.content }}</text>
        <image
          v-if="post.images && post.images[0]"
          :src="post.images[0]"
          class="post-image"
          mode="widthFix"
        />
        <view class="stats">
          <text><text class="luc luc-heart"></text> {{ post.likes || 0 }}</text>
          <text><text class="luc luc-message-circle"></text> {{ post.comments || 0 }}</text>
        </view>
      </view>

      <view class="comments-section">
        <text class="section-title">Comments ({{ (post.commentList || []).length }})</text>
        <view v-if="!(post.commentList && post.commentList.length)" class="no-comments">
          No comments yet
        </view>
        <view v-for="c in post.commentList || []" :key="c.id" class="comment-item">
          <text class="comment-user">{{ c.username }}</text>
          <text class="comment-content">{{ c.content }}</text>
          <text class="comment-time">{{ formatTime(c.createTime) }}</text>
        </view>
      </view>
    </template>

    <view class="comment-bar safe-area-bottom">
      <input
        v-model="commentText"
        class="comment-input"
        placeholder="Write a comment..."
        confirm-type="send"
        @confirm="onSendComment"
      >
      <view
        class="btn btn-primary send-btn"
        :class="{ disabled: !commentText }"
        @click="onSendComment"
      >
        Send
      </view>
    </view>
  </view>
</template>

<script>
import { communityApi } from '@/api'

export default {
  data() {
    return {
      postId: null,
      post: null,
      commentText: '',
      defaultAvatar: 'https://i.pravatar.cc/100?img=1',
    }
  },

  onLoad(query) {
    this.postId = query.id
    this.loadDetail()
  },

  methods: {
    async loadDetail() {
      try {
        this.post = await communityApi.getPostDetail(this.postId)
      } catch (e) {
        uni.showToast({ title: 'Failed to load post', icon: 'none' })
      }
    },

    async onSendComment() {
      if (!this.commentText) return
      try {
        await communityApi.addComment(this.postId, this.commentText)
        this.commentText = ''
        uni.showToast({ title: 'Comment posted', icon: 'success' })
        this.loadDetail()
      } catch (e) {
        uni.showToast({ title: 'Failed', icon: 'none' })
      }
    },

    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleString()
    },
  },
}
</script>

<style lang="scss" scoped>
.post-detail {
  min-height: 100vh;
  background: var(--color-background);
  padding-bottom: 120rpx;
}
.loading {
  text-align: center;
  padding: 64rpx;
  color: var(--color-text-tertiary);
}
.post-card {
  background: var(--color-surface);
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.post-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}
.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
}
.user-info {
  flex: 1;
}
.username {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  display: block;
}
.time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.content {
  font-size: var(--font-size-base);
  line-height: 1.6;
  margin-bottom: 16rpx;
}
.post-image {
  width: 100%;
  border-radius: var(--radius-sm);
  margin-bottom: 16rpx;
}
.stats {
  display: flex;
  gap: 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.comments-section {
  padding: 0 24rpx;
}
.section-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  margin-bottom: 16rpx;
  display: block;
}
.no-comments {
  text-align: center;
  padding: 32rpx;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.comment-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--color-divider);
}
.comment-user {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  display: block;
  margin-bottom: 4rpx;
}
.comment-content {
  font-size: var(--font-size-base);
  display: block;
  margin-bottom: 4rpx;
}
.comment-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.comment-bar {
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
.comment-input {
  flex: 1;
  padding: 16rpx 20rpx;
  background: var(--color-background);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-sm);
}
.send-btn {
  padding: 16rpx 32rpx;
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}
.send-btn.disabled {
  opacity: 0.5;
}
</style>
