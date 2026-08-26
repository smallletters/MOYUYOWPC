<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack"><text class="back-icon"><text class="luc luc-arrow-left"></text></text></view>
      <text class="nav-title">我的收藏</text>
      <view class="nav-placeholder" />
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!collected.length" class="empty">
      <text class="empty-text">还没有收藏任何帖子</text>
      <view class="empty-btn" @tap="goCommunity"><text class="empty-btn-text">去社区逛逛</text></view>
    </view>
    <view v-else class="post-list">
      <view v-for="post in collected" :key="post.id" class="post-card" @tap="openPost(post)">
        <text class="post-title">帖子 #{{ post.id }}</text>
        <text class="post-meta">收藏于 {{ formatTime(post.createTime) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { get } from '@/utils/request'
import { communityApi } from '@/api'

const collected = ref([])
const loading = ref(false)

async function loadCollected() {
  loading.value = true
  try {
    // 直接拉取收藏的帖子 id 列表
    const ids = await get('/api/v1/community/posts/collected')
    if (Array.isArray(ids) && ids.length) {
      const posts = []
      for (const id of ids) {
        try {
          const p = await communityApi.getPostDetail(id)
          posts.push(p)
        } catch (e) { /* ignore */ }
      }
      collected.value = posts
    } else {
      collected.value = []
    }
  } catch (e) {
    console.warn('[post-collection] load failed', e)
    collected.value = []
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}` } catch { return '' }
}

function openPost(post) {
  uni.navigateTo({ url: `/pages/community/detail?id=${post.id}` })
}

function goCommunity() { uni.switchTab({ url: '/pages/tabbar/community' }) }
function goBack() { uni.navigateBack() }

onMounted(() => { loadCollected() })
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--color-background); }
.nav-bar { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.nav-placeholder { width: 60rpx; }
.loading, .empty { padding: 80rpx 24rpx; text-align: center; }
.loading-text, .empty-text { font-size: 28rpx; color: var(--color-text-tertiary); }
.empty-btn { margin-top: 24rpx; display: inline-flex; align-items: center; padding: 16rpx 32rpx; background: var(--color-primary); border-radius: 999rpx; }
.empty-btn-text { color: #fff; font-size: 26rpx; }
.post-list { padding: 16rpx; display: flex; flex-direction: column; gap: 16rpx; }
.post-card { background: var(--color-surface); padding: 20rpx; border-radius: 16rpx; }
.post-title { display: block; font-size: 28rpx; font-weight: 600; color: var(--color-text); }
.post-meta { display: block; margin-top: 8rpx; font-size: 22rpx; color: var(--color-text-tertiary); }
</style>