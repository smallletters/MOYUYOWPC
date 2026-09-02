<template>
  <view class="page">


    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!collected.length" class="empty">
      <text class="empty-text">还没有收藏任何帖子</text>
      <view class="empty-btn" @tap="goCommunity">
        <text class="empty-btn-text">去社区逛逛</text>
      </view>
    </view>
    <view v-else class="post-list">
      <view
        v-for="post in collected"
        :key="post.id"
        class="post-card"
        @tap="openPost(post)">
        <view v-if="post.images && post.images.length" class="post-cover">
          <image :src="post.images[0]" mode="aspectFill" class="post-cover-img" />
        </view>
        <view class="post-content">
          <text class="post-title">{{ post.title || post.content || `帖子 #${post.id}` }}</text>
          <text v-if="post.content && post.title" class="post-excerpt">
            {{ truncate(post.content, 60) }}
          </text>
          <text class="post-meta">收藏于 {{ formatTime(post.createTime) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { get } from '@/utils/request'
import { communityApi } from '@/api'
import { usePageTitle } from '@/utils/i18nPageMixin'
usePageTitle('pageTitle.userPostCollection')


const collected = ref([])


const loading = ref(false)

async function loadCollected() {
  loading.value = true
  try {
    // 后端: GET /api/v1/community/posts/collected
    const page = await communityApi.getCollectedPosts({ page: 1, size: 50 })
    const records = Array.isArray(page?.records) ? page.records : []
    collected.value = records
  } catch (e) {
    console.warn('[post-collection] load failed', e)
    collected.value = []
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
  } catch {
    return ''
  }
}

function truncate(s, n) {
  if (!s) return ''
  return s.length > n ? s.slice(0, n) + '...' : s
}

function openPost(post) {
  uni.navigateTo({ url: `/pages/community/detail?id=${post.id}` })
}

function goCommunity() {
  uni.switchTab({ url: '/pages/tabbar/community' })
}
function goBack() {
  uni.navigateBack()
}

onMounted(() => {
  loadCollected()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--color-background);
}
.nav-back {
  width: 60rpx;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
}
.loading,
.empty {
  padding: 80rpx 24rpx;
  text-align: center;
}
.loading-text,
.empty-text {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
.empty-btn {
  margin-top: 24rpx;
  display: inline-flex;
  align-items: center;
  padding: 16rpx 32rpx;
  background: var(--color-primary);
  border-radius: 999rpx;
}
.empty-btn-text {
  color: #fff;
  font-size: 26rpx;
}
.post-list {
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.post-card {
  display: flex;
  gap: 20rpx;
  background: var(--color-surface);
  padding: 20rpx;
  border-radius: 16rpx;
}
.post-cover {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: 12rpx;
  overflow: hidden;
  background: var(--color-divider);
}
.post-cover-img {
  width: 100%;
  height: 100%;
}
.post-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.post-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.post-excerpt {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.post-meta {
  display: block;
  margin-top: auto;
  font-size: 22rpx;
  color: var(--color-text-tertiary);
}
</style>
