<template>
  <view class="page">
    <view class="header">
      <view class="nav-back" @tap="goBack"><text class="back-icon"><text class="luc luc-arrow-left"></text></text></view>
      <text class="title">成就墙</text>
    </view>

    <view v-if="loading" class="loading"><text class="loading-text">加载中…</text></view>
    <view v-else class="content">
      <view class="grid">
        <view v-for="a in achievements" :key="a.id" class="ach-card">
          <view class="ach-icon luc" :class="$luc(a.icon) || 'luc luc-trophy'"></view>
          <text class="ach-name">{{ a.name }}</text>
          <text class="ach-desc">{{ a.description }}</text>
          <text class="ach-reward">+{{ a.pointsReward || 0 }} 积分</text>
          <view class="ach-tag" :class="a.category ? 'tag-' + a.category.toLowerCase() : 'tag-common'">{{ a.category || 'COMMON' }}</view>
        </view>
        <view v-if="!achievements.length" class="empty"><text class="empty-text">暂无成就</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { marketingApi } from '@/api'

const achievements = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    achievements.value = await marketingApi.listAchievements() || []
  } catch (e) {
    console.warn('[achievement] load failed', e)
  } finally { loading.value = false }
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
.content { padding: 16rpx; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.ach-card { position: relative; padding: 24rpx; background: var(--color-surface); border-radius: 20rpx; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.ach-icon { font-size: 60rpx; }
.ach-name { font-size: 28rpx; font-weight: 600; text-align: center; }
.ach-desc { font-size: 22rpx; color: var(--color-text-tertiary); text-align: center; min-height: 60rpx; }
.ach-reward { font-size: 22rpx; color: var(--color-primary); font-weight: 600; }
.ach-tag { position: absolute; top: 8rpx; right: 8rpx; padding: 2rpx 10rpx; font-size: 18rpx; border-radius: 999rpx; background: #f0f0f0; color: var(--color-text-tertiary); }
.tag-common { background: #f0f0f0; color: #999; }
.tag-epic { background: #e6f7ff; color: var(--color-primary); }
.tag-rare { background: #fff7e6; color: #b8860b; }
.tag-legendary { background: #f9e6ff; color: #6f42c1; }
.empty { grid-column: 1/-1; padding: 60rpx 24rpx; text-align: center; }
.empty-text { font-size: 26rpx; color: var(--color-text-tertiary); }
</style>