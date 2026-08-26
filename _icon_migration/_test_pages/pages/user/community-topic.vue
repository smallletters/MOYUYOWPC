<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack"><text class="back-icon"><text class="luc luc-arrow-left"></text></text></view>
      <text class="nav-title">话题广场</text>
      <view class="nav-placeholder" />
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!topics.length" class="empty"><text class="empty-text">暂无可用话题</text></view>
    <view v-else class="topic-list">
      <view v-for="t in topics" :key="t.id" class="topic-card" @tap="enterTopic(t)">
        <text class="hash">#</text>
        <view class="info">
          <text class="name">{{ t.name }}</text>
          <text v-if="t.description" class="desc">{{ t.description }}</text>
          <view class="meta">
            <text class="meta-text">热度 {{ t.hot || 0 }}</text>
            <text v-if="t.postCount" class="meta-text">· {{ t.postCount }} 帖</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { get } from '@/utils/request'

const topics = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await get('/api/v1/community/topics')
    topics.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.warn('[community-topic] load failed', e)
  } finally { loading.value = false }
}

function enterTopic(t) {
  uni.navigateTo({ url: `/pages/community/detail?topicId=${t.id}&topicName=${encodeURIComponent(t.name)}` })
}
function goBack() { uni.navigateBack() }
onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--color-background); }
.nav-bar { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.nav-placeholder { width: 60rpx; }
.loading, .empty { padding: 80rpx 24rpx; text-align: center; }
.loading-text, .empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.topic-list { padding: 16rpx; display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; }
.topic-card { display: flex; gap: 12rpx; padding: 20rpx; background: var(--color-surface); border-radius: 16rpx; }
.hash { font-size: 32rpx; font-weight: 700; color: var(--color-primary); }
.info { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.name { font-size: 28rpx; font-weight: 600; }
.desc { font-size: 22rpx; color: var(--color-text-tertiary); }
.meta { display: flex; gap: 8rpx; margin-top: 6rpx; }
.meta-text { font-size: 22rpx; color: var(--color-text-tertiary); }
</style>