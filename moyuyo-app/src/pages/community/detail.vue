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
        <!-- 多图渲染：按 3 列九宫格展示；超过 9 张仍能滚动看（容器 wrap 即可） -->
        <view v-if="post.images && post.images.length" class="image-grid" :class="gridClass">
          <view
            v-for="(img, idx) in post.images"
            :key="idx"
            class="image-cell"
            @tap="previewImages(idx)"
          >
            <image :src="img" class="post-image" mode="aspectFill" />
          </view>
        </view>
        <view class="stats">
          <!-- 点赞按钮：liked=true 时高亮 + 心形变红；点击触发 onToggleLike -->
          <view class="stat-item" :class="{ liked: post.liked }" @tap="onToggleLike">
            <text class="luc luc-heart" />
            <text class="stat-num">{{ post.likes || 0 }}</text>
          </view>
          <view class="stat-item static">
            <text class="luc luc-message-circle" />
            <text class="stat-num">{{ post.comments || 0 }}</text>
          </view>
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
      // 点赞请求中标记：避免用户连续点击产生重复请求
      liking: false,
    }
  },

  computed: {
    // 图片数量决定网格列数与排布：
    // - 1 张：单图大图模式
    // - 2~4 张：两列
    // - 5~9 张：三列九宫格
    gridClass() {
      const n = (this.post?.images || []).length
      if (n <= 1) return 'grid-single'
      if (n <= 4) return 'grid-cols-2'
      return 'grid-cols-3'
    },
  },

  onLoad(query) {
    this.postId = query.id
    this.loadDetail()
  },

  methods: {
    async loadDetail() {
      try {
        const data = await communityApi.getPostDetail(this.postId)
        // 后端 VO 用 List<String> 存图片 URL；缺字段兜底为空数组，保证模板渲染安全
        this.post = {
          ...data,
          images: Array.isArray(data?.images) ? data.images : [],
        }
      } catch (e) {
        uni.showToast({ title: 'Failed to load post', icon: 'none' })
      }
    },

    /**
     * 点赞 / 取消点赞切换。
     * - 先乐观更新 UI（liked 取反 + likes ±1），再异步调接口；失败时回滚并提示
     * - liking 标记防止用户连点
     */
    async onToggleLike() {
      if (!this.post || this.liking) return
      const before = {
        liked: !!this.post.liked,
        likes: Number(this.post.likes) || 0,
      }
      const next = {
        liked: !before.liked,
        likes: before.likes + (before.liked ? -1 : 1),
      }
      // 乐观更新
      this.post.liked = next.liked
      this.post.likes = Math.max(0, next.likes)
      this.liking = true
      try {
        if (next.liked) {
          await communityApi.likePost(this.postId)
        } else {
          await communityApi.unlikePost(this.postId)
        }
      } catch (e) {
        // 失败回滚
        this.post.liked = before.liked
        this.post.likes = before.likes
        uni.showToast({ title: '操作失败，请重试', icon: 'none' })
      } finally {
        this.liking = false
      }
    },

    /**
     * 预览图片：使用 uni.previewImage 全屏查看当前帖子的全部图片，
     * 点击的索引 idx 作为 current，从该图开始展示。
     */
    previewImages(idx) {
      const urls = this.post?.images || []
      if (!urls.length) return
      uni.previewImage({ current: urls[idx], urls })
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

/* 多图九宫格：根据图片数量自动切换列数；单图占满宽度 */
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.image-cell {
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-background);
}

/* 三列（5~9 张）：每格约为容器宽度 1/3 减间距 */
.grid-cols-3 .image-cell {
  width: calc((100% - 16rpx) / 3);
  aspect-ratio: 1 / 1;
}

/* 两列（2~4 张）：每格约为容器宽度 1/2 减间距 */
.grid-cols-2 .image-cell {
  width: calc((100% - 8rpx) / 2);
  aspect-ratio: 1 / 1;
}

/* 单图：占满宽度，按原图比例展示 */
.grid-single .image-cell {
  width: 100%;
}

.image-cell .post-image {
  width: 100%;
  height: 100%;
  margin-bottom: 0;
  display: block;
}

.stats {
  display: flex;
  gap: 24rpx;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  align-items: center;
}

/* stat-item 兼容旧版 <text> 文本节点：保留 inline-flex 让心形+数字水平排列 */
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 4rpx 0;
}

/* 点赞高亮态：心形变红，加粗，让用户明确感知已点赞 */
.stat-item.liked {
  color: #ff4d4f;
}

.stat-item.static {
  cursor: default;
}

.stat-num {
  font-size: var(--font-size-sm);
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
