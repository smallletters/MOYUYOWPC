<template>
  <view class="page">
    <!-- 顶部导航:返回按钮 + 话题名 -->
    <view class="navbar safe-area-top">
      <view class="nav-back" @tap="goBack">
        <text class="luc luc-chevron-left" />
      </view>
      <view class="nav-title">
        <text class="hash">#</text>
        <text>{{ topicName || '话题' }}</text>
      </view>
      <view class="nav-spacer" />
    </view>

    <!-- 加载中 -->
    <view v-if="loading && posts.length === 0" class="status">
      <text class="status-text">加载中…</text>
    </view>

    <!-- 空结果 -->
    <view v-else-if="posts.length === 0" class="status">
      <text class="status-text">该话题下还没有帖子</text>
    </view>

    <!-- 帖子列表:复用搜索页的卡片样式 -->
    <view v-else class="post-list">
      <view
        v-for="p in posts"
        :key="p.id"
        class="post-card"
        @tap="goPostDetail(p.id)"
      >
        <view class="post-header">
          <image
            v-if="p.avatar"
            :src="resolveImageUrl(p.avatar)"
            class="post-avatar"
            mode="aspectFill"
            @error="onImageError"
          />
          <view v-else class="post-avatar post-avatar-fallback">
            {{ avatarChar(p.username) }}
          </view>
          <view class="post-user">
            <text class="post-username">{{ p.username || 'Pet Lover' }}</text>
            <text class="post-time">{{ formatTime(p.createTime) }}</text>
          </view>
        </view>
        <view class="post-content">
          <text class="post-text">{{ p.content || '(无内容)' }}</text>
          <view v-if="p.images && p.images.length" class="post-images">
            <image
              v-for="(img, idx) in p.images.slice(0, 3)"
              :key="idx"
              :src="resolveImageUrl(img)"
              class="post-thumb"
              mode="aspectFill"
            />
          </view>
        </view>
        <view class="post-stats">
          <text class="stat">♥ {{ p.likes || 0 }}</text>
          <text class="stat">💬 {{ p.comments || 0 }}</text>
        </view>
      </view>
    </view>

    <!-- 加载更多 / 到底提示 -->
    <view v-if="loading && posts.length > 0" class="status">
      <text class="status-text">加载中…</text>
    </view>
    <view v-if="!hasMore && posts.length > 0" class="status">
      <text class="status-text">— 没有更多了 —</text>
    </view>
  </view>
</template>

<script>
import { communityApi } from '@/api'

export default {
  pageTitleKey: 'pageTitle.userCommunityTopic',

  data() {
    return {
      topicId: null,
      topicName: '',
      posts: [],
      loading: false,
      hasMore: true,
      page: 1,
      pageSize: 20,
    }
  },

  onLoad(query) {
    // 社区主页点击话题传入 id + name
    if (query) {
      this.topicId = query.id || null
      this.topicName = (query.name && decodeURIComponent(query.name)) || ''
    }
    this.loadPosts(false)
  },

  // 触底加载更多
  onReachBottom() {
    if (this.loading || !this.hasMore) return
    this.loadPosts(true)
  },

  methods: {
    /**
     * 加载话题下的帖子列表。
     * 后端 searchPosts 支持 ?topic= 参数(精确/模糊匹配 mo_community_post.topic)
     * 走 search 接口的好处:自动带上 liked/collected 状态(VO 映射完成)
     */
    async loadPosts(loadMore) {
      if (this.loading) return
      // 加载更多前先翻页(初次加载 page=1,之后每次 +1)
      if (loadMore) this.page += 1
      this.loading = true
      try {
        const params = {
          topic: this.topicName,
          page: this.page,
          size: this.pageSize,
        }
        const res = await communityApi.searchCommunityPosts(params)
        const records = (res && res.records) || []
        this.posts = loadMore ? this.posts.concat(records) : records
        this.hasMore = records.length >= this.pageSize
      } catch (e) {
        console.error('[community-topic] load failed:', e)
        if (!loadMore) this.posts = []
        if (loadMore) this.page = Math.max(1, this.page - 1)
      } finally {
        this.loading = false
      }
    },

    goBack() {
      // 优先 navigateBack,没有可回退的栈时退回社区主页
      const pages = getCurrentPages ? getCurrentPages() : []
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.reLaunch({ url: '/pages/tabbar/community' })
      }
    },

    goPostDetail(id) {
      uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
    },

    resolveImageUrl(url) {
      if (!url) return ''
      if (url.startsWith('http://') || url.startsWith('https://')) return url
      // 相对路径(/uploads/...) APP 端无 dev server，必须拼上后端 base
      if (url.startsWith('/')) {
        const base = process.env.VITE_ADMIN_API_BASE
        return base ? `${base}${url}` : url
      }
      return url
    },

    onImageError() {
      // 静默失败
    },

    avatarChar(name) {
      if (!name) return 'P'
      return String(name).trim().charAt(0).toUpperCase()
    },

    formatTime(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      const now = new Date()
      const diff = (now - d) / 1000
      if (diff < 60) return '刚刚'
      if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
      if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
      if (diff < 7 * 86400) return Math.floor(diff / 86400) + '天前'
      const yyyy = d.getFullYear()
      const mm = String(d.getMonth() + 1).padStart(2, '0')
      const dd = String(d.getDate()).padStart(2, '0')
      return `${yyyy}-${mm}-${dd}`
    },
  },
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background, #f5f6f8);
}

/* 顶部导航 */
.navbar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface, #ffffff);
  border-bottom: 1rpx solid var(--color-divider, #ececec);
  position: sticky;
  top: 0;
  z-index: 10;
}
.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: var(--color-text, #1a1a1a);
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a1a);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}
.nav-title .hash {
  color: var(--color-primary, #18b367);
}
.nav-spacer {
  width: 60rpx;
}

.status {
  text-align: center;
  padding: 48rpx 0;
  color: var(--color-text-tertiary, #999);
  font-size: 26rpx;
}

.post-list {
  padding: 16rpx 24rpx;
}
.post-card {
  background: var(--color-surface, #ffffff);
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.post-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.post-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.post-avatar-fallback {
  background: var(--color-primary, #18b367);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}
.post-user {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.post-username {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a1a);
}
.post-time {
  font-size: 22rpx;
  color: var(--color-text-tertiary, #999);
  margin-top: 4rpx;
}
.post-text {
  font-size: 28rpx;
  color: var(--color-text, #1a1a1a);
  line-height: 1.5;
  display: block;
}
.post-images {
  display: flex;
  gap: 8rpx;
  margin-top: 12rpx;
}
.post-thumb {
  width: 180rpx;
  height: 180rpx;
  border-radius: 8rpx;
  object-fit: cover;
}
.post-stats {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--color-text-tertiary, #999);
}
</style>