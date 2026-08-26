<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon">‹</text></view>
      <text class="title">节日活动</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else-if="!festivals.length" class="empty"><text class="empty-text">暂无可参与的节日活动</text></view>
    <view v-else class="fest-list">
      <view v-for="f in festivals" :key="f.id" class="fest-card" @tap="enterFestival(f)">
        <view class="fest-cover">{{ f.icon || '🎉' }}</view>
        <view class="fest-info">
          <text class="fest-name">{{ f.name }}</text>
          <text v-if="f.description" class="fest-desc">{{ f.description }}</text>
          <text class="fest-period">{{ formatTime(f.startTime) }} ~ {{ formatTime(f.endTime) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const festivals = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    festivals.value = await marketingApi.listActiveFestivals() || []
  } catch (e) {
    console.warn('[festival] load failed', e)
  } finally { loading.value = false }
}

function formatTime(t) {
  if (!t) return ''
  try { const d = new Date(t); return `${d.getMonth() + 1}-${d.getDate()}` } catch { return '' }
}

function enterFestival(f) {
  uni.navigateTo({ url: `/pages/goods/list?festivalId=${f.id}` })
}

function goBack() { uni.navigateBack() }
onMounted(() => { load() })
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--color-background); }
.header { display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: var(--color-surface); border-bottom: 1rpx solid var(--color-divider); }
.nav-back { width: 60rpx; }
.back-icon { font-size: 44rpx; color: var(--color-primary); }
.title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; }
.loading { padding: 80rpx 24rpx; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.empty { padding: 80rpx 24rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
.fest-list { padding: 16rpx; display: flex; flex-direction: column; gap: 16rpx; }
.fest-card { display: flex; gap: 16rpx; padding: 16rpx; background: var(--color-surface); border-radius: 16rpx; }
.fest-cover { width: 140rpx; height: 140rpx; border-radius: 12rpx; background: linear-gradient(135deg, #ee5a52, #ff9a9e); display: flex; align-items: center; justify-content: center; font-size: 60rpx; }
.fest-info { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.fest-name { font-size: 30rpx; font-weight: 700; }
.fest-desc { font-size: 24rpx; color: var(--color-text-secondary); }
.fest-period { font-size: 22rpx; color: var(--color-text-tertiary); }
</style>